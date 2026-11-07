/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        navy: {
          950: '#04070F',
          900: '#070B18',
          850: '#0A1020',
          800: '#0D1527',
          750: '#111B31',
          700: '#16223B',
          600: '#1E2C49',
          500: '#2A3B5E',
        },
        brand: {
          50: '#EEF3FF',
          100: '#DCE6FF',
          200: '#BCCEFF',
          300: '#93AEFF',
          400: '#7298FF',
          500: '#5B8CFF',
          600: '#3F6FE8',
          700: '#3157BE',
          800: '#2A4796',
          900: '#273F7A',
        },
        accent: {
          400: '#4DE3F5',
          500: '#22D3EE',
          600: '#0FB6CE',
        },
      },
      fontFamily: {
        sans: [
          'Inter Variable',
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(91,140,255,0.18), 0 18px 60px -24px rgba(63,111,232,0.45)',
        card: '0 1px 0 rgba(255,255,255,0.04) inset, 0 18px 40px -28px rgba(0,0,0,0.9)',
        drawer: '-24px 0 60px -30px rgba(0,0,0,0.9)',
      },
      backgroundImage: {
        'grid-faint':
          'linear-gradient(rgba(148,163,184,0.055) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.055) 1px, transparent 1px)',
        'aurora':
          'radial-gradient(1200px 600px at 10% -10%, rgba(63,111,232,0.28), transparent 60%), radial-gradient(900px 500px at 100% 0%, rgba(34,211,238,0.14), transparent 55%), radial-gradient(800px 600px at 50% 120%, rgba(124,58,237,0.16), transparent 60%)',
      },
      backgroundSize: {
        grid: '36px 36px',
      },
      keyframes: {
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        'fade-in-up': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-in-right': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-400px 0' },
          '100%': { backgroundPosition: '400px 0' },
        },
        'pulse-ring': {
          '0%': { boxShadow: '0 0 0 0 rgba(34,211,238,0.45)' },
          '70%': { boxShadow: '0 0 0 8px rgba(34,211,238,0)' },
          '100%': { boxShadow: '0 0 0 0 rgba(34,211,238,0)' },
        },
        flash: {
          '0%': { backgroundColor: 'rgba(91,140,255,0.16)' },
          '100%': { backgroundColor: 'transparent' },
        },
      },
      animation: {
        'fade-in': 'fade-in 180ms ease-out both',
        'fade-in-up': 'fade-in-up 260ms cubic-bezier(0.2, 0.7, 0.3, 1) both',
        'slide-in-right': 'slide-in-right 240ms cubic-bezier(0.2, 0.7, 0.3, 1) both',
        shimmer: 'shimmer 1.4s linear infinite',
        'pulse-ring': 'pulse-ring 2s ease-out infinite',
        flash: 'flash 1.4s ease-out both',
      },
    },
  },
  plugins: [],
};
