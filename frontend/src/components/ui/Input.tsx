import React from 'react';
import TextField from '@mui/material/TextField';

interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size' | 'color'> {
  label: string;

  helper?: string;
  error?: string;

  endAdornment?: React.ReactNode;

  multiline?: boolean;
  minRows?: number;

  ref?: React.Ref<HTMLInputElement>;
}









export function Input({ label, helper, error, className = '', id, endAdornment, ref, ...props }: InputProps) {



  const { 'aria-label': ariaLabel, ...rest } = props as {
    'aria-label'?: string;
  } & typeof props;



  const inputSlotProps: Record<string, unknown> = {};
  if (ariaLabel) inputSlotProps['aria-label'] = ariaLabel;
  if (endAdornment) inputSlotProps.endAdornment = endAdornment;









  const slotProps: Record<string, Record<string, unknown>> = {
    inputLabel: { shrink: true },
  };
  if (Object.keys(inputSlotProps).length > 0) {
    slotProps.input = inputSlotProps;
  }

  return (
    <TextField
      label={label}
      id={id}
      className={className}
      fullWidth
      size="small"
      margin="none"
      helperText={error ?? helper}
      error={Boolean(error)}
      inputRef={ref}
      slotProps={slotProps}
      {...rest}
    />
  );
}
