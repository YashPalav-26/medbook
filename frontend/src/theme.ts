import { createTheme } from '@mui/material/styles';

const brand = {
  50: '#effaf7',
  100: '#d7f2ec',
  200: '#b0e5da',
  300: '#7dd1c2',
  400: '#45b5a5',
  500: '#22998b',
  600: '#167b71',
  700: '#145f5a',
  800: '#134c49',
  900: '#123f3d',
};

const ink = {
  50: '#f6f8f8',
  100: '#eceff0',
  200: '#d5dcde',
  300: '#b1bfc3',
  400: '#8599a0',
  500: '#677d85',
  600: '#52656d',
  700: '#435258',
  800: '#3a464b',
  900: '#343e42',
  950: '#1c2427',
};

export const theme = createTheme({
  cssVariables: true,
  palette: {
    mode: 'light',
    primary: {
      main: brand[600],
      light: brand[400],
      dark: brand[800],
      contrastText: '#ffffff',
    },
    secondary: {
      main: ink[100],
      dark: ink[200],
      contrastText: ink[800],
    },
    success: { main: '#067a55', contrastText: '#ffffff' }, // success-700
    warning: { main: '#a16207', contrastText: '#ffffff' }, // warning-700
    error: { main: '#dc2626', dark: '#b91c1c', contrastText: '#ffffff' }, // danger-600/700
    info: { main: '#1d4ed8', contrastText: '#ffffff' }, // info-700
    background: {
      default: ink[50],
      paper: '#ffffff',
    },
    text: {
      primary: ink[900],
      secondary: ink[500],
      disabled: ink[400],
    },
    divider: ink[200],
    action: {
      hover: ink[100],
      hoverOpacity: 1,
      selected: brand[50],
      disabledOpacity: 0.55,
    },
  },

  typography: {
    fontFamily:
      '"Inter", ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
    h1: { fontFamily: '"Source Serif 4", Georgia, serif', fontWeight: 600 },
    h2: { fontFamily: '"Source Serif 4", Georgia, serif', fontWeight: 600 },
    h3: { fontFamily: '"Source Serif 4", Georgia, serif', fontWeight: 600 },
    h4: { fontFamily: '"Source Serif 4", Georgia, serif', fontWeight: 600 },
    h5: { fontFamily: '"Source Serif 4", Georgia, serif', fontWeight: 600 },
    h6: { fontWeight: 600 },
    subtitle2: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 500 },
    fontSize: 14.5,
  },

  shape: {
    borderRadius: 10, // radius-field (0.625rem)
  },

  shadows: [
    'none',
    '0 1px 3px rgb(28 36 39 / 0.06), 0 1px 2px rgb(28 36 39 / 0.04)', // shadow-card
    '0 2px 6px rgb(28 36 39 / 0.08)',
    '0 4px 10px -2px rgb(28 36 39 / 0.10)',
    '0 6px 16px -4px rgb(28 36 39 / 0.14), 0 2px 6px -2px rgb(28 36 39 / 0.08)', // shadow-lift
    '0 24px 60px -12px rgb(28 36 39 / 0.35)', // shadow-modal
    ...Array<string>(19).fill('none'),
  ] as unknown as import('@mui/material/styles').Shadows,

  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: 10,

          gap: 8,
          whiteSpace: 'nowrap',
          boxShadow: '0 1px 3px rgb(28 36 39 / 0.06)',
        },
        sizeSmall: { padding: '6px 12px', fontSize: '0.875rem' },
        sizeMedium: { padding: '8px 16px', fontSize: '0.875rem' },
        sizeLarge: { padding: '10px 20px', fontSize: '1rem' },
      },

      variants: [

        {
          props: { variant: 'contained', color: 'primary' },
          style: {
            '&:hover': { backgroundColor: brand[700] },
            '&:active': { backgroundColor: brand[800] },
          },
        },

        {
          props: { variant: 'text', color: 'primary' },
          style: {
            '&:hover': { backgroundColor: brand[50] },
          },
        },

        {
          props: { variant: 'outlined', color: 'inherit' },
          style: {
            borderColor: ink[200],
            color: ink[700],
            '&:hover': {
              backgroundColor: ink[50],
              borderColor: ink[300],
              color: ink[900],
            },
          },
        },

        {
          props: { variant: 'text', color: 'inherit' },
          style: {
            color: ink[700],
            '&:hover': { backgroundColor: ink[100], color: ink[900] },
            '&:active': { backgroundColor: ink[200] },
          },
        },

        {
          props: { variant: 'outlined', color: 'error' },
          style: {
            borderColor: '#fca5a5', // danger-300
            color: '#b91c1c', // danger-700
            '&:hover': {
              backgroundColor: '#fee2e266', // danger-100 at 40%
              borderColor: '#dc2626', // danger-600
              color: '#b91c1c',
            },
          },
        },

        {
          props: { variant: 'contained', color: 'error' },
          style: {
            '&:hover': { backgroundColor: '#b91c1c' },
            '&:active': { backgroundColor: '#991b1b' },
          },
        },

        {
          props: { variant: 'outlined', color: 'info' },
          style: {
            borderColor: 'rgba(255,255,255,0.55)',
            color: '#ffffff',
            '&:hover': {
              borderColor: '#ffffff',
              backgroundColor: 'rgba(255,255,255,0.14)',
            },
          },
        },

        {
          props: { variant: 'contained', color: 'info' },
          style: {
            backgroundColor: '#ffffff',
            color: brand[700],
            boxShadow: '0 1px 3px rgb(28 36 39 / 0.10)',
            '&:hover': { backgroundColor: brand[50], color: brand[800] },
            '&:active': { backgroundColor: brand[100] },
          },
        },

        {
          props: { variant: 'text', color: 'info' },
          style: {
            color: '#ffffff',
            '&:hover': { backgroundColor: 'rgba(255,255,255,0.16)', color: '#ffffff' },
            '&:active': { backgroundColor: 'rgba(255,255,255,0.24)' },
          },
        },
      ],
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          backgroundColor: '#ffffff',
          '& fieldset': { borderColor: ink[200] },
          '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: brand[500] },
        },
      },
    },
    MuiInputLabel: {
      styleOverrides: {
        root: { color: ink[700], '&.Mui-focused': { color: brand[600] } },
      },
    },
    MuiIconButton: {
      variants: [

        {
          props: { color: 'error' },
          style: {
            color: '#b91c1c', // danger-700
            '&:hover': { backgroundColor: '#fee2e2' }, // danger-100
          },
        },
      ],
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 500 },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: { borderRadius: 14, backgroundImage: 'none' },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: { backgroundImage: 'none' },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 10 },
      },
    },
    MuiTooltip: {
      defaultProps: { arrow: true },
    },
    MuiTableCell: {
      styleOverrides: {
        head: {
          fontWeight: 600,
          color: ink[500],
          fontSize: '0.75rem',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          backgroundColor: ink[50],
        },
      },
    },
  },
});

export const statusColors = {
  SCHEDULED: '#38bdf8', // info
  CONFIRMED: '#16a34a', // success green
  COMPLETED: '#6b7280', // ink-500
  CANCELLED: '#dc2626', // danger-600
  NO_SHOW: '#d97706', // warning-600
} as const;
