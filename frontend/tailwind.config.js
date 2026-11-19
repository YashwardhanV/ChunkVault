/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#0b0f14',
        paper: '#f3f5f0',
        lime: '#a8ff4f',
        coral: '#7657ff',
        mist: '#e7ebe4',
      },
      boxShadow: {
        lift: '0 24px 70px rgba(11, 15, 20, 0.18)',
      },
    },
  },
  plugins: [],
}
