import React from 'react';
import TextField from '@mui/material/TextField';

interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size' | 'color'> {
  label: string;
  /** Optional helper line shown under the control. */
  helper?: string;
  error?: string;
  /** Trailing control inside the field (e.g. a password visibility toggle). */
  endAdornment?: React.ReactNode;
  /** Renders a textarea instead of a single-line input. */
  multiline?: boolean;
  minRows?: number;
  /** react-hook-form's register() ref — must reach the real <input>, not the wrapper. */
  ref?: React.Ref<HTMLInputElement>;
}

/**
 * MedBook text field — MUI TextField (outlined).
 *
 * Label, helper text, error message and aria-invalid wiring are handled by
 * MUI (the label is programmatically associated via the generated id), so the
 * control keeps the accessible behavior of the previous in-house field while
 * using the third-party implementation.
 */
export function Input({ label, helper, error, className = '', id, endAdornment, ref, ...props }: InputProps) {
  // MUI spreads unknown props onto the FormControl root, so a caller's
  // aria-label would never reach the actual <input>. Route it through the
  // htmlInput slot (MUI v9 removed the legacy inputProps prop).
  const { 'aria-label': ariaLabel, ...rest } = props as {
    'aria-label'?: string;
  } & typeof props;

  // MUI routes OutlinedInput props through slotProps.input (v9); putting the
  // adornment there keeps it inside the field outline and out of the text area.
  const inputSlotProps: Record<string, unknown> = {};
  if (ariaLabel) inputSlotProps['aria-label'] = ariaLabel;
  if (endAdornment) inputSlotProps.endAdornment = endAdornment;

  // The floating label is pinned to its shrunk position from the first render.
  //
  // MUI derives the label transform, the input padding AND the outline notch
  // (the gap the label sits in) from this one flag, and it evaluates them when
  // the field first mounts — if `shrink` is left to flip later, the styles can
  // be computed for the unshrunk state and the label then overlaps the border
  // line and the input text (date/time fields are the worst case, because their
  // native "mm/dd/yyyy" placeholder cannot be hidden).
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
