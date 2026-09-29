#!/usr/bin/env python3
"""
End-to-end smoke test against a running stack: the real API, real MySQL and Mailpit for the emails.
Standard library only, so it runs anywhere Python 3.10+ is installed.

    python3 scripts/smoke_test.py                                    # docker compose stack on :8088
    BASE_URL=http://localhost:8080 python3 scripts/smoke_test.py     # API started directly

Set DEVIDP_USER_PASSWORD (from .env) to also run the OAuth2 + PKCE login against the local dev IdP.
It finishes by tripping a honeytoken, which blocks this machine's IP for 15 minutes
(set SKIP_HONEYPOT=1 to leave that out while you are still using the app).
"""
import http.cookiejar
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("BASE_URL", "http://localhost:8088").rstrip("/")
MAIL = os.environ.get("MAILPIT_URL", "http://localhost:8025").rstrip("/") + "/api/v1"
DEVIDP_EMAIL = os.environ.get("DEVIDP_USER_EMAIL", "demo@shopspring.dev")
DEVIDP_PASSWORD = os.environ.get("DEVIDP_USER_PASSWORD")
PASSWORD = "correct horse battery staple 9"


class LocalhostPolicy(http.cookiejar.DefaultCookiePolicy):
    """Browsers treat http://localhost as a secure context and send Secure cookies to it; do the same."""

    def return_ok_secure(self, cookie, request):
        return urllib.parse.urlparse(request.full_url).hostname in ("localhost", "127.0.0.1") \
            or super().return_ok_secure(cookie, request)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Client:
    def __init__(self, ua="smoke/1.0"):
        self.jar = http.cookiejar.CookieJar(LocalhostPolicy())
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar), NoRedirect())
        self.token = None
        self.ua = ua

    def req(self, method, path, body=None, headers=None, form=False):
        url = path if path.startswith("http") else BASE + path
        h = {"User-Agent": self.ua, "X-Requested-With": "XMLHttpRequest"}
        if body is not None:
            if form:
                h["Content-Type"] = "application/x-www-form-urlencoded"
                body = urllib.parse.urlencode(body).encode()
            else:
                h["Content-Type"] = "application/json"
                body = json.dumps(body).encode()
        if self.token:
            h["Authorization"] = "Bearer " + self.token
        h.update(headers or {})
        request = urllib.request.Request(url, data=body, headers=h, method=method)
        try:
            resp = self.opener.open(request, timeout=20)
            status, data, headers = resp.status, resp.read(), resp.headers
        except urllib.error.HTTPError as e:
            status, data, headers = e.code, e.read(), e.headers
        ctype = headers.get("Content-Type", "")
        if data and ("json" in ctype):
            data = json.loads(data)
        elif isinstance(data, bytes):
            data = data.decode("utf-8", "replace")
        return status, data, headers

    def cookie(self, name):
        return next((c.value for c in self.jar if c.name == name), None)


def mail_code(to):
    for _ in range(60):
        messages = json.load(urllib.request.urlopen(MAIL + "/messages?limit=50"))["messages"] or []
        for m in messages:
            if any(t["Address"] == to for t in m["To"]) and "code" in m["Subject"]:
                full = json.load(urllib.request.urlopen(MAIL + "/message/" + m["ID"]))
                code = re.search(r"\b(\d{6})\b", full["Text"])
                if code:
                    return code.group(1)
        time.sleep(0.5)
    raise SystemExit("No OTP email arrived for " + to)


def clear_mail():
    urllib.request.urlopen(urllib.request.Request(MAIL + "/messages", method="DELETE"))


failures = 0


def check(owasp, label, ok, detail=""):
    global failures
    print(f"{'PASS' if ok else 'FAIL'}  [{owasp}] {label}" + ("" if ok else f"\n      {detail}"))
    if not ok:
        failures += 1


def wait_for_api():
    for _ in range(90):
        try:
            if Client().req("GET", "/auth/providers")[0] == 200:
                return
        except OSError:
            pass
        time.sleep(2)
    raise SystemExit("API did not come up at " + BASE)


wait_for_api()
clear_mail()

# ---- Registration with an emailed one-time code
email = f"asha.{int(time.time())}@example.com"
c = Client()
s, ch, _ = c.req("POST", "/auth/register",
                 {"firstName": "Asha", "lastName": "Kulkarni", "email": email, "password": PASSWORD})
check("A07", "register returns an OTP challenge", s == 202, (s, ch))
s, auth, h = c.req("POST", "/auth/register/verify", {"challengeId": ch["challengeId"], "code": mail_code(email)})
check("A07", "OTP verifies the email and signs in", s == 200 and "accessToken" in auth, (s, auth))
cookie = h.get("Set-Cookie", "")
check("A07", "refresh cookie is HttpOnly, SameSite=Strict, Path=/auth",
      all(x in cookie for x in ("HttpOnly", "SameSite=Strict", "Path=/auth")), cookie)
c.token = auth["accessToken"]
s, me, _ = c.req("GET", "/api/users/me")
check("A01", "access token works on the API", s == 200 and me["email"] == email, (s, me))
check("A04", "no password hash in the user JSON", "password" not in json.dumps(me).lower(), me)

# ---- Password + OTP sign-in on a second device
clear_mail()
c2 = Client("smoke-second-device/2.0")
s, ch, _ = c2.req("POST", "/auth/login", {"email": email, "password": PASSWORD})
check("A07", "correct password leads to an OTP step, not a token", s == 200 and ch["purpose"] == "LOGIN", (s, ch))
s, err, _ = c2.req("POST", "/auth/login/verify", {"challengeId": ch["challengeId"], "code": "000000"})
check("A07", "wrong OTP is refused and counted", s == 400 and "4 attempts" in err.get("detail", ""), (s, err))
code = mail_code(email)
s, auth2, _ = c2.req("POST", "/auth/login/verify", {"challengeId": ch["challengeId"], "code": code})
check("A07", "right OTP signs in", s == 200, (s, auth2))
s, err, _ = c2.req("POST", "/auth/login/verify", {"challengeId": ch["challengeId"], "code": code})
check("A07", "a used OTP can't be replayed", s == 400, (s, err))

# ---- Refresh token rotation and theft detection
old = c2.cookie("ss_refresh")
s, r1, _ = c2.req("POST", "/auth/refresh")
check("A07", "refresh rotates the cookie", s == 200 and c2.cookie("ss_refresh") != old, (s, r1))
thief = Client("thief/1.0")
s, r, _ = thief.req("POST", "/auth/refresh", headers={"Cookie": "ss_refresh=" + old})
check("A07", "a stolen, already-used refresh token is refused", s == 401, (s, r))
s, r, _ = c2.req("POST", "/auth/refresh")
check("A07", "reuse revokes the whole session", s == 401, (s, r))
c2.token = r1["accessToken"]
s, r, _ = c2.req("GET", "/api/users/me")
check("A07", "the revoked session's access token stops working", s == 401, (s, r))

# ---- Enumeration and CSRF
s1, e1, _ = Client().req("POST", "/auth/login", {"email": email, "password": "wrong password 123"})
s2, e2, _ = Client().req("POST", "/auth/login", {"email": "nobody@example.com", "password": "wrong password 123"})
check("A07", "same error for a wrong password and an unknown email",
      s1 == s2 == 401 and e1.get("detail") == e2.get("detail"), (e1, e2))
s, r, _ = Client().req("POST", "/auth/refresh", headers={"X-Requested-With": "", "Origin": "https://evil.example"})
check("A01", "cross-site call to a cookie endpoint is blocked", s == 403, (s, r))

# ---- Cart, order and payment integrity
s, page, _ = c.req("GET", "/api/products?pageSize=1&category=women_dress")
p = page["content"][0]
s, cart, _ = c.req("PUT", "/api/cart/add", {"productId": p["id"], "size": "M", "quantity": 2, "price": 1})
check("A06", "the client's price is ignored", s == 200 and cart["totalDiscountedPrice"] == 2 * p["discountedPrice"],
      (s, cart))
address = {"firstName": "Asha", "lastName": "K", "streetAddress": "1 MG Road", "city": "Pune",
           "state": "Maharashtra", "zipCode": "411001", "mobile": "+91 9876543210"}
s, order, _ = c.req("POST", "/api/orders/", address)
check("A06", "order waits for payment", s == 201 and order["orderStatus"] == "PENDING_PAYMENT", (s, order))
s, pay, _ = c.req("POST", f"/api/payments/{order['id']}")
check("A06", "payment amount comes from the server", s == 200 and pay["amount"] == order["totalDiscountedPrice"] * 100,
      (s, pay))
s, _, h = c.req("POST", "/dev/mock-gateway/pay", {"providerOrderId": pay["providerOrderId"], "outcome": "success"},
                form=True)
check("A08", "gateway redirects back with a signed result", s == 303, s)
result = dict(urllib.parse.parse_qsl(urllib.parse.urlparse(h["Location"]).query))
s, r, _ = c.req("POST", "/api/payments/verify", {"orderId": order["id"], **result, "signature": "0" * 64})
check("A08", "a forged payment signature is refused", s == 400, (s, r))
s, paid, _ = c.req("POST", "/api/payments/verify", {"orderId": order["id"], **result})
check("A08", "a genuine signature marks the order paid", s == 200 and paid["orderStatus"] == "PLACED", (s, paid))
s, again, _ = c.req("POST", "/api/payments/verify", {"orderId": order["id"], **result})
check("A08", "verifying twice is idempotent", s == 200 and again["orderStatus"] == "PLACED", (s, again))

# ---- Access control
s, _, _ = Client().req("GET", f"/api/orders/{order['id']}")
check("A01", "orders need a token", s == 401, s)
s, _, _ = c.req("GET", "/api/admin/users")
check("A01", "a customer can't reach admin endpoints", s == 403, s)
s, rv, _ = c.req("POST", "/api/reviews/create", {"productId": p["id"], "review": "<script>alert(1)</script> Lovely"})
check("A05", "a verified buyer can review", s == 201, (s, rv))

# ---- Response headers
s, _, h = Client().req("GET", "/api/products?pageSize=1")
check("A02", "API sends nosniff and no-store", h.get("X-Content-Type-Options") == "nosniff"
      and "no-store" in (h.get("Cache-Control") or ""), dict(h))
if BASE.endswith(":8088"):
    s, _, h = Client().req("GET", "/")
    csp = h.get("Content-Security-Policy", "")
    check("A05", "web app CSP forbids inline script", s == 200 and "script-src 'self'" in csp
          and "unsafe-inline" not in csp.split("script-src")[1].split(";")[0], csp)

# ---- OAuth2 login with PKCE against the local OpenID Connect provider
if DEVIDP_PASSWORD:
    o = Client("smoke-oauth2/1.0")
    # Look like a browser navigation: the IdP only remembers the authorize request for page loads.
    html = {"Accept": "text/html,*/*", "X-Requested-With": ""}
    s, _, h = o.req("GET", "/oauth2/authorization/devidp")
    authorize = h.get("Location", "")
    q = dict(urllib.parse.parse_qsl(urllib.parse.urlparse(authorize).query))
    check("A07", "authorization request uses PKCE S256, state and nonce",
          s == 302 and q.get("code_challenge_method") == "S256" and q.get("state") and q.get("nonce"), authorize)
    check("A01", "redirect_uri is the configured public URL", q.get("redirect_uri", "").startswith(BASE + "/login/oauth2/code/"),
          q.get("redirect_uri"))
    s, _, h = o.req("GET", authorize, headers=html)
    login_url = urllib.parse.urljoin(authorize, h.get("Location", "/login"))
    s, page, _ = o.req("GET", login_url, headers=html)
    csrf = re.search(r'name="_csrf"[^>]*value="([^"]+)"', page) or re.search(r'value="([^"]+)"[^>]*name="_csrf"', page)
    s, _, h = o.req("POST", login_url, {"username": DEVIDP_EMAIL, "password": DEVIDP_PASSWORD,
                                        "_csrf": csrf.group(1) if csrf else ""}, form=True, headers=html)
    next_url = urllib.parse.urljoin(login_url, h.get("Location", ""))
    for _ in range(5):    # IdP: saved request -> authorize -> redirect back to the app with a code
        if urllib.parse.urlparse(next_url).path.startswith("/login/oauth2/code/"):
            break
        s, _, h = o.req("GET", next_url, headers=html)
        next_url = urllib.parse.urljoin(next_url, h.get("Location", ""))
    s, _, h = o.req("GET", next_url, headers=html)
    landing = h.get("Location", "")
    check("A07", "code exchange lands on /oauth2/callback with no token in the URL",
          s == 302 and landing.endswith("/oauth2/callback") and "token" not in landing, (s, landing))
    s, oauth, _ = o.req("POST", "/auth/refresh")
    check("A07", "OAuth2 sign-in yields a session for the IdP's verified email",
          s == 200 and oauth["user"]["email"] == DEVIDP_EMAIL and oauth["user"]["authProvider"] == "DEVIDP",
          (s, oauth))

# ---- Honeytoken: the last check, because it blocks this IP for 15 minutes
if not os.environ.get("SKIP_HONEYPOT"):
    s, _, _ = Client().req("GET", "/.env")
    check("A09", "probing /.env looks like a normal 404", s == 404, s)
    s, _, _ = Client().req("GET", "/api/products?pageSize=1")
    check("A09", "the prober is then blocked", s == 403, s)

print(f"\n{'All checks passed' if failures == 0 else f'{failures} check(s) failed'}")
sys.exit(1 if failures else 0)
