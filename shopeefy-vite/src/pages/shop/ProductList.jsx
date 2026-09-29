import { useState } from 'react';
import { useParams, useSearchParams } from 'react-router';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Radio from '@mui/material/Radio';
import FormControlLabel from '@mui/material/FormControlLabel';
import TextField from '@mui/material/TextField';
import Pagination from '@mui/material/Pagination';
import TuneOutlined from '@mui/icons-material/TuneOutlined';
import ProductCard from '../../components/ProductCard';
import { EmptyState, ErrorAlert } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { COLORS, DISCOUNTS, PRICE_RANGES, SIZES, SORTS, categoryName } from '../../config/catalog';

const PAGE_SIZE = 12;

const csv = (value) => (value ? value.split(',').filter(Boolean) : []);

function toApiParams(category, params) {
  const [minPrice, maxPrice] = (params.get('price') ?? '').split('-');
  const page = Math.max(1, Number.parseInt(params.get('page') ?? '1', 10) || 1);
  const query = {
    category,
    color: params.get('color') || undefined,
    size: params.get('size') || undefined,
    minPrice: minPrice || undefined,
    maxPrice: maxPrice || undefined,
    minDiscount: params.get('discount') || undefined,
    stock: params.get('stock') || undefined,
    sort: params.get('sort') || 'newest',
    pageNumber: page - 1,
    pageSize: PAGE_SIZE,
  };
  return [Object.fromEntries(Object.entries(query).filter(([, v]) => v !== undefined)), page];
}

function FilterGroup({ title, children }) {
  return (
    <fieldset className="border-b border-slate-200 pb-4">
      <legend className="mb-2 text-sm font-semibold text-slate-800">{title}</legend>
      <div className="flex flex-col">{children}</div>
    </fieldset>
  );
}

function Filters({ params, update }) {
  const colors = csv(params.get('color'));
  const sizes = csv(params.get('size'));
  const toggle = (key, list, value) => {
    const next = list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
    update({ [key]: next.join(',') });
  };
  const single = (key, value, label, testId) => (
    <FormControlLabel
      key={value || 'any'}
      label={label}
      sx={{ '& .MuiFormControlLabel-label': { fontSize: 14 } }}
      control={
        <Radio
          size="small"
          checked={(params.get(key) ?? '') === value}
          onChange={() => update({ [key]: value })}
          slotProps={{ input: { 'data-testid': testId } }}
        />
      }
    />
  );

  return (
    <div className="space-y-4" data-testid="filters">
      <FilterGroup title="Colour">
        <div className="grid grid-cols-2">
          {COLORS.map((color) => (
            <FormControlLabel
              key={color}
              label={color}
              sx={{ '& .MuiFormControlLabel-label': { fontSize: 14, textTransform: 'capitalize' } }}
              control={
                <Checkbox
                  size="small"
                  checked={colors.includes(color)}
                  onChange={() => toggle('color', colors, color)}
                  slotProps={{ input: { 'data-testid': `filter-color-${color.replace(' ', '-')}` } }}
                />
              }
            />
          ))}
        </div>
      </FilterGroup>

      <FilterGroup title="Size">
        <div className="flex gap-2">
          {SIZES.map((size) => (
            <Button
              key={size}
              size="small"
              variant={sizes.includes(size) ? 'contained' : 'outlined'}
              onClick={() => toggle('size', sizes, size)}
              sx={{ minWidth: 44 }}
              data-testid={`filter-size-${size}`}
            >
              {size}
            </Button>
          ))}
        </div>
      </FilterGroup>

      <FilterGroup title="Price">
        {single('price', '', 'Any price', 'filter-price-any')}
        {PRICE_RANGES.map((r) => single('price', r.value, r.label, `filter-price-${r.value}`))}
      </FilterGroup>

      <FilterGroup title="Discount">
        {single('discount', '', 'Any discount', 'filter-discount-any')}
        {DISCOUNTS.map((d) => single('discount', String(d), `${d}% and above`, `filter-discount-${d}`))}
      </FilterGroup>

      <FilterGroup title="Availability">
        {single('stock', '', 'All products', 'filter-stock-any')}
        {single('stock', 'in_stock', 'In stock', 'filter-stock-in_stock')}
        {single('stock', 'out_of_stock', 'Out of stock', 'filter-stock-out_of_stock')}
      </FilterGroup>
    </div>
  );
}

export default function ProductList() {
  const { category } = useParams();
  const [params, setParams] = useSearchParams();
  const [showFilters, setShowFilters] = useState(false);
  const [apiParams, page] = toApiParams(category, params);
  const { data, error, loading } = useApi('/api/products', apiParams);

  const update = (changes, resetPage = true) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === '' || value == null) next.delete(key);
      else next.set(key, value);
    }
    if (resetPage) next.delete('page');
    setParams(next);
  };

  const activeFilters = ['color', 'size', 'price', 'discount', 'stock'].some((k) => params.get(k));
  const title = category ? categoryName(category) : 'All products';

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold capitalize text-slate-900" data-testid="listing-title">
            {title}
          </h1>
          <p className="text-sm text-slate-500" data-testid="product-count">
            {data ? `${data.totalElements} product${data.totalElements === 1 ? '' : 's'}` : ' '}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            startIcon={<TuneOutlined />}
            onClick={() => setShowFilters((v) => !v)}
            variant="outlined"
            size="small"
            sx={{ display: { lg: 'none' } }}
          >
            Filters
          </Button>
          {activeFilters && (
            <Button
              size="small"
              onClick={() => update({ color: '', size: '', price: '', discount: '', stock: '' })}
              data-testid="filters-clear"
            >
              Clear filters
            </Button>
          )}
          <TextField
            select
            size="small"
            label="Sort by"
            value={params.get('sort') || 'newest'}
            onChange={(e) => update({ sort: e.target.value === 'newest' ? '' : e.target.value })}
            slotProps={{ select: { native: true }, htmlInput: { 'data-testid': 'sort-select' } }}
            sx={{ minWidth: 190 }}
          >
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </TextField>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[230px_1fr]">
        <aside className={`${showFilters ? 'block' : 'hidden'} lg:block`}>
          <Filters params={params} update={update} />
        </aside>
        <div>
          <ErrorAlert error={error} sx={{ mb: 2 }} />
          {loading && !data ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
              {Array.from({ length: 8 }, (_, i) => (
                <div key={i} className="aspect-[4/5] animate-pulse rounded-xl bg-slate-200" />
              ))}
            </div>
          ) : data && data.content.length === 0 ? (
            <EmptyState title="No products match these filters">Try removing a filter or two.</EmptyState>
          ) : (
            <div
              className={`grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4 ${loading ? 'opacity-60' : ''}`}
              data-testid="product-grid"
            >
              {data?.content.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
          {data && data.totalPages > 1 && (
            <div className="mt-8 flex justify-center">
              <Pagination
                count={Math.min(data.totalPages, 1001)}
                page={page}
                color="primary"
                onChange={(_, value) => update({ page: value === 1 ? '' : String(value) }, false)}
                data-testid="pagination"
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
