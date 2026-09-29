import { useSearchParams } from 'react-router';
import ProductCard from '../../components/ProductCard';
import { EmptyState, ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';

export default function Search() {
  const [params] = useSearchParams();
  const q = (params.get('q') ?? '').trim().slice(0, 100);
  const { data, error, loading } = useApi(q ? '/api/products/search' : null, { q });

  return (
    <div>
      <PageTitle subtitle={data ? `${data.length} result${data.length === 1 ? '' : 's'} (showing up to 20)` : null}>
        {q ? <>Results for “{q}”</> : 'Search'}
      </PageTitle>
      <ErrorAlert error={error} />
      {!q && <EmptyState title="What are you looking for?">Try “kurta”, “black jeans” or “cotton dress”.</EmptyState>}
      {loading && <Loading label="Searching…" />}
      {data && data.length === 0 && (
        <EmptyState title="Nothing found">
          Search matches whole words of 3 or more letters. Try a different word.
        </EmptyState>
      )}
      {data && data.length > 0 && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4 xl:grid-cols-5" data-testid="search-results">
          {data.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </div>
  );
}
