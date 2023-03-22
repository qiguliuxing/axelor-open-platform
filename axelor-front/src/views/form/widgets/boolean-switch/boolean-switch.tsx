import { InputLabel, Switch } from "@axelor/ui";
import { FieldContainer, FieldProps } from "../../builder";
import { useAtom } from "jotai";

export function BooleanSwitch({
  schema,
  readonly,
  valueAtom,
}: FieldProps<boolean>) {
  const { uid, title } = schema;
  const [value = false, setValue] = useAtom(valueAtom);
  return (
    <FieldContainer readonly={readonly}>
      <InputLabel htmlFor={uid}>{title}</InputLabel>
      <Switch
        id={uid}
        checked={value ?? false}
        onChange={(e) => setValue(!value)}
        value=""
      />
    </FieldContainer>
  );
}
