import { useAtom, useAtomValue } from "jotai";
import { useCallback, useMemo, useState } from "react";
import { Box, SelectProps } from "@axelor/ui";

import { MaterialIcon } from "@axelor/ui/icons/material-icon";

import {
  Select,
  SelectIcon,
  SelectOptionProps,
  SelectValue,
} from "@/components/select";
import { useAsyncEffect } from "@/hooks/use-async-effect";
import { usePermitted } from "@/hooks/use-permitted";
import {
  useBeforeSelect,
  useCompletion,
  useCreateOnTheFly,
  useEditor,
  useEditorInTab,
  useEnsureRelated,
  useFieldRelated,
  useSelector,
} from "@/hooks/use-relation";
import { DataContext, DataRecord } from "@/services/client/data.types";
import { toKebabCase } from "@/utils/names";

import { usePermission, usePrepareWidgetContext } from "../../builder/form";
import { FieldControl } from "../../builder/form-field";
import { useFormRefresh } from "../../builder/scope";
import { FieldProps } from "../../builder/types";
import { removeVersion } from "../../builder/utils";
import { ViewerInput, ViewerLink } from "../string/viewer";
import { useOptionLabel } from "./utils";
import { Schema } from "@/services/client/meta.types";
import { makeImageURL } from "../image/utils";
import styles from "./many-to-one.module.css";

export function ManyToOne(
  props: FieldProps<DataRecord> & {
    isSuggestBox?: boolean;
    selectProps?: Partial<SelectProps<DataRecord, false>>;
  },
) {
  const {
    schema,
    formAtom,
    valueAtom,
    widgetAtom,
    readonly: _readonly,
    invalid,
    isSuggestBox,
    selectProps,
  } = props;
  const {
    target,
    targetName,
    targetSearch,
    canSuggest = true,
    placeholder,
    orderBy: sortBy,
    formView,
    gridView,
    limit,
    searchLimit,
    perms,
    imageField,
    colorField,
  } = schema;
  const [value, setValue] = useAtom(valueAtom);
  const [hasSearchMore, setSearchMore] = useState(false);
  const { hasButton } = usePermission(schema, widgetAtom, perms);
  const { attrs } = useAtomValue(widgetAtom);
  const { title, focus, required, domain, hidden } = attrs;

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
    (changedValue: SelectValue<DataRecord, false>) => {
      if (changedValue && changedValue.id && changedValue.id > 0) {
        const rec = removeVersion(changedValue);
        setValue(rec, true, rec.id !== value?.id);
      } else {
        setValue(changedValue, true);
      }
    },
    [setValue, value],
  );

  const canRead = perms?.read !== false;
  const canView = value && hasButton("view");
  const canEdit = value && hasButton("edit") && attrs.canEdit;
  const canNew = hasButton("new") && attrs.canNew;
  const canSelect = hasButton("select");
  const isRefLink = schema.widget === "ref-link";
  const readonly = _readonly || !canRead;

  const isPermitted = usePermitted(target, perms);

  const handleEdit = useCallback(
    async (readonly = false, record?: DataContext) => {
      const $record = record ?? value;
      if (!(await isPermitted($record, readonly))) {
        return;
      }
      if (showEditorInTab && ($record?.id ?? 0) > 0) {
        return showEditorInTab($record!, readonly);
      }
      showEditor({
        title: title ?? "",
        model: target,
        viewName: formView,
        record: $record,
        readonly,
        context: {
          _parent: getContext(),
        },
        onSelect: handleChange,
      });
    },
    [
      value,
      isPermitted,
      showEditorInTab,
      showEditor,
      title,
      target,
      formView,
      getContext,
      handleChange,
    ],
  );

  const handleView = useCallback(
    async (e: React.MouseEvent<HTMLButtonElement>) => {
      e.preventDefault();
      if (isRefLink && showEditorInTab && value?.id) {
        return showEditorInTab(value, true);
      }
      return handleEdit(true);
    },
    [isRefLink, value, handleEdit, showEditorInTab],
  );

  const showCreate = useCallback(
    (input: string, popup = true) =>
      showCreator({
        input,
        popup,
        onEdit: (record) => handleEdit(false, record),
        onSelect: handleChange,
      }),
    [handleEdit, handleChange, showCreator],
  );

  const showCreateAndSelect = useCallback(
    (input: string) => showCreate(input, false),
    [showCreate],
  );

  const [beforeSelect, { onMenuOpen, onMenuClose }] = useBeforeSelect(
    schema,
    getContext,
  );

  const related = useFieldRelated({ field: schema });
  const { ensureRelated, updateRelated, valueRef } = useEnsureRelated({
    field: schema,
    formAtom,
    valueAtom,
    related,
  });

  const showSelect = useCallback(async () => {
    const _domain = await beforeSelect(domain, true);
    const _domainContext = _domain ? getContext() : {};
    showSelector({
      model: target,
      viewName: gridView,
      orderBy: sortBy,
      multiple: false,
      domain: _domain,
      context: _domainContext,
      limit: searchLimit,
      ...(canNew && {
        onCreate: () => showCreate(""),
      }),
      onSelect: async (records) => {
        const value = await ensureRelated(records[0]);
        handleChange(value);
      },
    });
  }, [
    canNew,
    beforeSelect,
    domain,
    getContext,
    showSelector,
    showCreate,
    target,
    gridView,
    sortBy,
    searchLimit,
    ensureRelated,
    handleChange,
  ]);

  const fetchOptions = useCallback(
    async (text: string) => {
      const _domain = await beforeSelect(domain);
      const _domainContext = _domain ? getContext() : {};
      const options = {
        _domain,
        _domainContext,
      };
      if (!target) {
        return [];
      }

      const { records, page } = await search(text, options);
      setSearchMore((page.totalCount ?? 0) > records.length);
      return records;
    },
    [beforeSelect, domain, getContext, search, target],
  );

  const ensureRelatedValues = useCallback(
    async (signal?: AbortSignal, refetch?: boolean) => {
      // only handle ref-select
      if (value && schema.related) {
        updateRelated(value, refetch);
      }
    },
    [value, schema.related, updateRelated],
  );

  const onRefSelectRefresh = useCallback(() => {
    if (["ref-select", "ref-link"].includes(toKebabCase(schema.widget))) {
      valueRef.current = null;
      ensureRelatedValues(undefined, true);
    }
  }, [schema.widget, valueRef, ensureRelatedValues]);

  const getOptionKey = useCallback((option: DataRecord) => option.id!, []);
  const getOptionLabel = useOptionLabel(schema);
  const getOptionEqual = useCallback(
    (a: DataRecord, b: DataRecord) => a.id === b.id,
    [],
  );

  const getOptionMatch = useCallback(() => true, []);

  const icons: SelectIcon[] = useMemo(() => {
    const edit: SelectIcon = {
      icon: <MaterialIcon icon="edit" />,
      onClick: () => handleEdit(),
    };
    const view: SelectIcon = {
      icon: <MaterialIcon icon="description" />,
      onClick: () => handleEdit(true),
    };
    const add: SelectIcon = {
      icon: <MaterialIcon icon="add" />,
      onClick: () => handleEdit(false, { id: null }),
    };
    const find: SelectIcon = {
      icon: <MaterialIcon icon="search" />,
      onClick: showSelect,
    };

    const result: SelectIcon[] = [];

    if (target) {
      if (canEdit && canView) result.push(edit);
      if (isSuggestBox) return result;
      if (!canEdit && canView) result.push(view);
      if (canNew) result.push(add);
      if (canSelect) result.push(find);
    }

    return result;
  }, [
    canEdit,
    canNew,
    canSelect,
    canView,
    handleEdit,
    showSelect,
    isSuggestBox,
    target,
  ]);

  useAsyncEffect(ensureRelatedValues, [ensureRelatedValues]);

  // register form:refresh
  useFormRefresh(onRefSelectRefresh);

  const selectPropsWithImage = {
    inputStartAdornment: useMemo(
      () =>
        value && (
          <RelationalValueImage
            field={imageField}
            model={target}
            value={value}
          />
        ),
      [value, target, imageField],
    ),
    renderOption: useCallback(
      ({ option }: SelectOptionProps<DataRecord>) => (
        <RelationalValue schema={schema} value={option} />
      ),
      [schema],
    ),
  };

  if (hidden) {
    return null;
  }

  return (
    <FieldControl {...props}>
      {readonly &&
        (value && hasButton("view") ? (
          <ViewerLink onClick={handleView}>
            {selectProps?.renderOption ? (
              selectProps.renderOption({ option: value })
            ) : (
              <RelationalValue schema={schema} value={value} />
            )}
          </ViewerLink>
        ) : (
          <ViewerInput name={schema.name} value={getOptionLabel(value)} />
        ))}
      {readonly || (
        <Select
          autoFocus={focus}
          required={required}
          invalid={invalid}
          canSelect={canSelect}
          autoComplete={canSuggest}
          fetchOptions={fetchOptions}
          options={[] as DataRecord[]}
          optionKey={getOptionKey}
          optionLabel={getOptionLabel}
          optionEqual={getOptionEqual}
          optionMatch={getOptionMatch}
          value={value}
          placeholder={placeholder}
          onChange={handleChange}
          onOpen={onMenuOpen}
          onClose={onMenuClose}
          canCreateOnTheFly={canNew && schema.create}
          canShowNoResultOption={true}
          onShowCreate={canNew ? showCreate : undefined}
          onShowSelect={canSelect && hasSearchMore ? showSelect : undefined}
          onShowCreateAndSelect={
            canNew && schema.create ? showCreateAndSelect : undefined
          }
          icons={icons}
          clearIcon={false}
          toggleIcon={isSuggestBox ? undefined : false}
          {...(imageField && selectPropsWithImage)}
          {...selectProps}
        />
      )}
    </FieldControl>
  );
}

function RelationalValueImage({
  field,
  model,
  value,
}: {
  field?: string;
  model?: string;
  value?: null | DataRecord;
}) {
  const src = makeImageURL(value, model, field);
  return <img className={styles.image} src={src} />;
}

export function RelationalValue({
  value,
  schema,
}: {
  schema: Schema;
  value?: null | DataRecord;
}) {
  const { target, imageField } = schema;
  const getOptionLabel = useOptionLabel(schema);

  return (
    <Box d="flex" gap={4} alignItems="center">
      {imageField && (
        <RelationalValueImage value={value} field={imageField} model={target} />
      )}
      {getOptionLabel(value)}
    </Box>
  );
}
