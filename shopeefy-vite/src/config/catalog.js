/** Third-level category names as the API knows them, grouped for the menu and the home page. */
export const DEPARTMENTS = [
  {
    id: 'women',
    name: 'Women',
    categories: [
      { id: 'women_dress', name: 'Dresses' },
      { id: 'top', name: 'Tops' },
      { id: 'women_jeans', name: 'Women’s jeans' },
      { id: 'lengha_choli', name: 'Lengha choli' },
    ],
  },
  {
    id: 'men',
    name: 'Men',
    categories: [
      { id: 'mens_kurta', name: 'Kurtas' },
      { id: 'shirt', name: 'Shirts' },
      { id: 'men_jeans', name: 'Men’s jeans' },
      { id: 'pant', name: 'Pants' },
    ],
  },
];

export const CATEGORIES = DEPARTMENTS.flatMap((d) => d.categories.map((c) => ({ ...c, department: d.name })));

export function categoryName(id) {
  return CATEGORIES.find((c) => c.id === id)?.name ?? id?.replaceAll('_', ' ');
}

export const COLORS = [
  'white',
  'black',
  'grey',
  'yellow',
  'blue',
  'dark blue',
  'maroon',
  'green',
  'pink',
  'red',
  'orange',
  'purple',
  'multicolor',
];

export const SIZES = ['S', 'M', 'L'];

export const PRICE_RANGES = [
  { value: '0-499', label: 'Under ₹500' },
  { value: '500-999', label: '₹500 to ₹999' },
  { value: '1000-1999', label: '₹1,000 to ₹1,999' },
  { value: '2000-4999', label: '₹2,000 to ₹4,999' },
  { value: '5000-', label: '₹5,000 and above' },
];

export const DISCOUNTS = [10, 30, 50, 70];

export const SORTS = [
  { value: 'newest', label: 'Newest' },
  { value: 'price_low', label: 'Price: low to high' },
  { value: 'price_high', label: 'Price: high to low' },
];
