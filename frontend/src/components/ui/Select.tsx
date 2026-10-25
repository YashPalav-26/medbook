import React from 'react';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import MuiSelect from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormHelperText from '@mui/material/FormHelperText';

/**
 * Minimal shape of the event both call-site styles consume. MUI's Select
 * onChange receives a widened union event that cannot be named from outside
 * the library; the runtime contract is that `event.target.value` carries the
 * selected option value, which is exactly what controlled handlers and
 * react-hook-form's register() spread both read.
 */
export interface SelectChangeEventLike {
  target: { value: string };
}

interface SelectProps {
  label: string;
  helper?: string;
  error?: string;
  className?: string;
  id?: string;
  disabled?: boolean;
  required?: boolean;
  /** Provided by react-hook-form's register() spread. */
  name?: string;
  /** Provided by react-hook-form's register() spread. */
  onBlur?: (...event: never[]) => void;
  /** Controlled onChange (value/onChange) or the register() spread. */
  onChange?: (event: SelectChangeEventLike) => void;
  /** Provided by react-hook-form's register() spread. */
  ref?: React.Ref<HTMLDivElement>;
  value?: string | number | null;
  children?: React.ReactNode;
}

/**
 * MedBook select field — the MUI Select rendered inside a TextField-style
 * outlined control (MUI's documented `select` composition: outlined border,
 * notched label, menu listbox). The trigger, popup, keyboard navigation and
 * ARIA (combobox/listbox/option semantics) all come from MUI.
 *
 * Call sites may keep declaring options with plain `<option>` elements — the
 * adapter maps them onto MUI MenuItems internally (MUI v9's Select requires
 * its list items to be focusable, which bare `<option>` elements are not).
 *
 * Compatible with both call-site styles in the app:
 * - controlled: `<Select label value onChange>`
 * - react-hook-form: `<Select label {...register('x', { valueAsNumber: true })}>`
 *   (uncontrolled; register's name/onChange/onBlur/ref are routed to MUI)
 */
export function Select({
  label,
  helper,
  error,
  className = '',
  id,
  disabled,
  required,
  name,
  onBlur,
  onChange,
  ref,
  value,
  children,
  ...rest
}: SelectProps) {
  const selectId = id ?? React.useId();
  const labelId = `${selectId}-label`;
  const helperId = helper || error ? `${selectId}-helper` : undefined;

  // Map <option> children onto MenuItems so the listbox is keyboard-navigable
  // and pointer-selectable (MUI list items must carry a tabindex).
  const items = React.Children.toArray(children).map((child) => {
    if (React.isValidElement(child) && child.type === 'option') {
      const props = child.props as {
        value?: string | number;
        children?: React.ReactNode;
        disabled?: boolean;
      };
      return (
        <MenuItem key={child.key ?? String(props.value)} value={props.value} disabled={props.disabled}>
          {props.children}
        </MenuItem>
      );
    }
    return child;
  });

  // MUI's onChange/onBlur parameter types are unnameable unions; the handlers
  // we accept (controlled handlers and register() spreads) both read
  // event.target.value, which is the runtime contract — cast at the boundary.
  const handlerProps = { name, onBlur, onChange } as unknown as React.ComponentProps<
    typeof MuiSelect
  >;

  return (
    <FormControl
      fullWidth
      size="small"
      required={required}
      error={Boolean(error)}
      className={className}
    >
      {/* labelId (not htmlFor) is MUI's association mechanism for Select: the
          trigger gets aria-labelledby, which label-click users and testing-
          library label queries both honor. */}
      <InputLabel id={labelId}>{label}</InputLabel>
      <MuiSelect
        labelId={labelId}
        id={selectId}
        label={label}
        value={value}
        disabled={disabled}
        inputRef={ref}
        {...handlerProps}
        {...rest}
      >
        {items}
      </MuiSelect>
      {(error || helper) && <FormHelperText id={helperId}>{error ?? helper}</FormHelperText>}
    </FormControl>
  );
}
