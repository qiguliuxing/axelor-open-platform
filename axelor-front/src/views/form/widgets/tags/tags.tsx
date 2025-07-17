import { useAtom, useAtomValue } from "jotai";
import { selectAtom } from "jotai/utils";
import getObjValue from "lodash/get";
import isEqual from "lodash/isEqual";
import { useCallback, useMemo, useRef, useState } from "react";
import { SelectRefHandler } from "@axelor/ui";

import { RelationalTag } from "@/components/tag";
import { Select, SelectOptionProps, SelectValue } from "@/components/select";
import { useAsyncEffect } from "@/hooks/use-async-effect";
import {
  useBeforeSelect,
  useCompletion,
  useCreateOnTheFly,
  useEditor,
  useEditorInTab,
  useSelector,
} from "@/hooks/use-relation";
import { useOptionLabel } from "../many-to-one/utils";
import { DataSource } from "@/services/client/data";
import { DataContext, DataRecord } from "@/services/client/data.types";
import { toKebabCase } from "@/utils/names";

import {
  FieldControl,
  FieldProps,
  usePermission,
  usePrepareWidgetContext,
} from "../../builder";
import { removeVersion } from "../../builder/utils";

const EMPTY: DataRecord[] = [];

export function Tags(props: FieldProps<any>) {
  const { schema, formAtom, valueAtom, widgetAtom, readonly, invalid } = props;
  const {
    name,
    target,
    targetName,
    targetSearch,
    colorField,
    canSuggest = true,
    placeholder,
    orderBy: sortBy,
    formView,
    gridView,
    limit,
    searchLimit,
  } = schema;

  const isManyToMany =
    toKebabCase(schema.serverType || schema.widget) === "many-to-many";

  const [value, setValue] = useAtom(valueAtom);
  const [hasSearchMore, setSearchMore] = useState(false);
  const { hasButton } = usePermission(schema, widgetAtom);

  const parentId = useAtomValue(
    useMemo(() => selectAtom(formAtom, (form) => form.record.id), [formAtom]),
  );
  const parentModel = useAtomValue(
    useMemo(() => selectAtom(formAtom, (form) => form.model), [formAtom]),
  );

  const selectRef = useRef<SelectRefHandler>(null);
  const valueRef = useRef<DataRecord[]>();
  const { attrs } = useAtomValue(widgetAtom);
  const { title, focus, required, domain } = attrs;

  const getContext = usePrepareWidgetContext(schema, formAtom, widgetAtom);
  const showSelector = useSelector();
  const showEditor = useEditor();
  const showEditorInTab = useEditorInTab(schema);
  const showCreator = useCreateOnTheFly(schema);

  const fetchFields = useMemo<string[]>(
    () => (colorField ? [colorField] : []),
    [colorField],
  );

  const search = useCompletion({
    sortBy,
    limit,
    target,
    targetName,
    targetSearch,
    fetchFields,
  });

  const handleChange = useCallback(
    (changedValue: SelectValue<DataRecord, true>) => {
      if (Array.isArray(changedValue)) {
        const items = changedValue.map(removeVersion);
        const prev = value ?? [];
        const markDirty =
          !isManyToMany ||
          prev.length !== items.length ||
          items.some((item, index) => item.id !== prev[index].id);
        setValue(items.length === 0 ? null : items, true, markDirty);
      } else {
        setValue(changedValue, true);
      }
    },
    [isManyToMany, setValue, value],
  );

  const handleSelect = useCallback(
    (record: DataRecord) => {
      const next = Array.isArray(value) ? [...value] : [];
      const index = next.findIndex((x) => x.id === record.id);
      if (index >= 0) {
        const found = next[index];
        next[index] = { ...found, ...record };
      } else {
        next.push(record);
      }
      handleChange(next);
    },
    [handleChange, value],
  );

  const canNew = hasButton("new");
  const canView = hasButton("view");
  const canEdit = hasButton("edit") && attrs.canEdit !== false;
  const canSelect = hasButton("select");
  const canRemove = !readonly && attrs.canRemove !== false;

  const [ready, setReady] = useState(false);

  const ensureRelated = useCallback(
    async (value: DataRecord[]) => {
      const names = [targetName, ...fetchFields].filter(Boolean);
      const ids = value
        .filter((v) => names.some((name) => getObjValue(v, name) === undefined))
        .map((v) => v.id);

      const missing = names.filter((name) =>
        value.some((v) => getObjValue(v, name) === undefined),
      );

      if (missing.length > 0 && ids.length > 0) {
        let records: DataRecord[] = [];
        try {
          const ds = new DataSource(target);
          records = await ds
            .search({
              fields: missing,
              filter: {
                _domain: "self.id in (:_field_ids)",
                _domainContext: {
                  _model: target,
                  _field: name,
                  _field_ids: ids as number[],
                  _parent: {
                    id: parentId,
                    _model: parentModel,
                  },
                },
              },
            })
            .then((res) => res.records);
        } catch (er) {
          records = ids.map((id) => ({
            id,
            [targetName]: value.find((x) => x.id === id)?.[targetName] ?? id,
          }));
        }
        const newValue = value.map((v) => {
          const rec = records.find((r) => r.id === v.id);
          return rec
            ? missing.reduce((acc, name) => ({ ...acc, [name]: rec[name] }), v)
            : v;
        });
        return newValue;
      }
      return value;
    },
    [targetName, fetchFields, target, name, parentId, parentModel],
  );

  const ensureRelatedValues = useCallback(
    async (signal?: AbortSignal) => {
      if (valueRef.current === value) return;
      if (value) {
        const newValue = await ensureRelated(value);
        if (!isEqual(newValue, value)) {
          valueRef.current = newValue;
          if (signal?.aborted) return;
          setValue(newValue, false, false);
        } else {
          valueRef.current = value;
        }
      }
      setReady(true);
    },
    [ensureRelated, setValue, value],
  );

  const handleEdit = useCallback(
    async (record?: DataContext) => {
      // close dropdown selection
      selectRef.current?.close?.();

      if (canEdit && showEditorInTab && (record?.id ?? 0) > 0) {
        return showEditorInTab(record!, readonly);
      }
      showEditor({
        title: title ?? "",
        model: target,
        viewName: formView,
        record,
        readonly: readonly || !canEdit,
        context: {
          _parent: getContext(),
        },
        onSelect: handleSelect,
      });
    },
    [
      showEditorInTab,
      showEditor,
      title,
      target,
      formView,
      readonly,
      canEdit,
      getContext,
      handleSelect,
    ],
  );

  const handleRemove = useCallback(
    (record: DataRecord) => {
      if (Array.isArray(value)) {
        handleChange(value.filter((x) => x.id !== record.id));
      }
    },
    [handleChange, value],
  );

  const [beforeSelect, { onMenuOpen, onMenuClose }] = useBeforeSelect(
    schema,
    getContext,
  );

  const showCreate = useCallback(
    (input: string, popup = true) =>
      showCreator({
        input,
        popup,
        onEdit: handleEdit,
        onSelect: handleSelect,
      }),
    [handleEdit, handleSelect, showCreator],
  );

  const showSelect = useCallback(async () => {
    const _domain = await beforeSelect(domain, true);
    const _domainContext = _domain ? getContext() : {};
    showSelector({
      model: target,
      viewName: gridView,
      orderBy: sortBy,
      multiple: true,
      domain: _domain,
      context: _domainContext,
      limit: searchLimit,
      ...(canNew && {
        onCreate: () => showCreate(""),
      }),
      onSelect: async (records = []) => {
        const all = Array.isArray(value) ? value : [];
        const add = records.filter((x) => !all.some((a) => a.id === x.id));
        handleChange([...all, ...add]);
      },
    });
  }, [
    canNew,
    beforeSelect,
    domain,
    getContext,
    showCreate,
    showSelector,
    target,
    gridView,
    sortBy,
    searchLimit,
    value,
    handleChange,
  ]);

  const showCreateAndSelect = useCallback(
    (input: string) => showCreate(input, false),
    [showCreate],
  );

  const fetchOptions = useCallback(
    async (text: string) => {
      const _domain = await beforeSelect(domain);
      const _domainContext = _domain ? getContext() : {};
      const options = {
        _domain,
        _domainContext,
      };
      const { records, page } = await search(text, options);
      setSearchMore((page.totalCount ?? 0) > records.length);
      return records;
    },
    [beforeSelect, domain, getContext, search],
  );

  const getOptionLabel = useOptionLabel(schema);
  const getOptionKey = useCallback((option: DataRecord) => option.id!, []);
  const getOptionEqual = useCallback(
    (a: DataRecord, b: DataRecord) => a.id === b.id,
    [],
  );

  const getOptionMatch = useCallback(() => true, []);

  const renderOption = useCallback(
    ({ option }: SelectOptionProps<DataRecord>) => {
      return <RelationalTag value={option} schema={schema} />;
    },
    [schema],
  );

  const renderValue = useCallback(
    ({ option }: SelectOptionProps<DataRecord>) => {
      return (
        <RelationalTag
          value={option}
          schema={schema}
          onClick={canView ? handleEdit : undefined}
          onRemove={canRemove ? handleRemove : undefined}
        />
      );
    },
    [canRemove, canView, schema, handleEdit, handleRemove],
  );

  useAsyncEffect(ensureRelatedValues, [ensureRelatedValues]);

  return (
    <FieldControl {...props}>
      <Select
        autoFocus={focus}
        multiple={true}
        readOnly={readonly}
        required={required}
        invalid={invalid}
        canSelect={canSelect}
        autoComplete={canSuggest}
        selectRef={selectRef}
        fetchOptions={fetchOptions}
        options={[] as DataRecord[]}
        optionKey={getOptionKey}
        optionLabel={getOptionLabel}
        optionEqual={getOptionEqual}
        optionMatch={getOptionMatch}
        value={ready ? (value ?? EMPTY) : EMPTY}
        placeholder={placeholder}
        onChange={handleChange}
        onOpen={onMenuOpen}
        onClose={onMenuClose}
        canShowNoResultOption={true}
        canCreateOnTheFly={canNew && schema.create}
        onShowCreate={canNew ? showCreate : undefined}
        onShowCreateAndSelect={
          canNew && schema.create ? showCreateAndSelect : undefined
        }
        onShowSelect={canSelect && hasSearchMore ? showSelect : undefined}
        clearIcon={false}
        renderValue={renderValue}
        renderOption={renderOption}
      />
    </FieldControl>
  );
}
