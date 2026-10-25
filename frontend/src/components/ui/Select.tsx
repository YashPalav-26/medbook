import React from 'react';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import MuiSelect from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormHelperText from '@mui/material/FormHelperText';








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

  name?: string;

  onBlur?: (...event: never[]) => void;

  onChange?: (event: SelectChangeEventLike) => void;

  ref?: React.Ref<HTMLDivElement>;
  value?: string | number | null;
  children?: React.ReactNode;
}
















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
