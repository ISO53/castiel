import { createContext } from "reka-ui";

export const [injectQuestionnaireRootContext, provideQuestionnaireRootContext] =
  createContext("Questionnaire");

export const [injectQuestionnaireItemContext, provideQuestionnaireItemContext] =
  createContext("QuestionnaireItem");

export function hasInputValue(value) {
  if (Array.isArray(value)) {
    return value.some((item) => String(item).trim().length > 0);
  }

  return (
    value !== undefined && value !== null && String(value).trim().length > 0
  );
}

export function getShortcutKeys(shortcuts) {
  if (shortcuts === "letters") {
    return Array.from({ length: 26 }, (_, index) =>
      String.fromCharCode(65 + index),
    );
  }

  if (shortcuts === "numbers") {
    return Array.from({ length: 9 }, (_, index) => String(index + 1));
  }

  return [];
}

export function getShortcutFromKey(key, shortcuts) {
  const normalizedKey = shortcuts === "letters" ? key.toUpperCase() : key;

  return getShortcutKeys(shortcuts).includes(normalizedKey)
    ? normalizedKey
    : null;
}

export function getAnswerKeyShortcuts(shortcut, filled) {
  return (
    [shortcut, filled ? "Enter" : null].filter(Boolean).join(" ") || undefined
  );
}

export function isAnswerFilled(answer) {
  if (answer.type === "choice") {
    return answer.element.checked;
  }

  return (
    answer.element.hasAttribute("name") && hasInputValue(answer.element.value)
  );
}

export function isEmptyNavigableInput(answer) {
  return (
    answer?.type === "input" &&
    ["email", "password", "search", "tel", "text", "url"].includes(
      answer.element.type,
    ) &&
    !hasInputValue(answer.element.value)
  );
}

export function isTextEntryTarget(element) {
  if (
    element instanceof HTMLTextAreaElement ||
    element instanceof HTMLSelectElement
  ) {
    return true;
  }

  if (element instanceof HTMLInputElement) {
    return !["button", "checkbox", "radio", "reset", "submit"].includes(
      element.type,
    );
  }

  return element instanceof HTMLElement && element.isContentEditable;
}

export function isRadioTarget(element) {
  return element instanceof HTMLInputElement && element.type === "radio";
}

/**
 * Sort registrations by the position of their element in the document, so that
 * navigation always follows the rendered order instead of the mount order.
 */
export function compareDocumentOrder(first, second) {
  if (first === second) {
    return 0;
  }

  const position = first.compareDocumentPosition(second);

  if (position & Node.DOCUMENT_POSITION_FOLLOWING) {
    return -1;
  }

  if (position & Node.DOCUMENT_POSITION_PRECEDING) {
    return 1;
  }

  return 0;
}

export function createQuestionnaireCollection(items) {
  if (items === undefined) {
    return null;
  }

  return {
    enabledItems: items.filter((item) => !item.disabled),
    itemByName: new Map(items.map((item) => [item.name, item])),
    items,
  };
}

export function getInitialItemName(collection, defaultItem) {
  if (!collection) {
    return defaultItem ?? null;
  }

  const defaultDefinition = defaultItem
    ? collection.itemByName.get(defaultItem)
    : undefined;

  if (defaultDefinition && !defaultDefinition.disabled) {
    return defaultDefinition.name;
  }

  return collection.enabledItems[0]?.name ?? null;
}

/**
 * Map every enabled choice of an item definition to a keyboard shortcut, so
 * that shortcuts stay stable regardless of how choices are rendered.
 */
export function getShortcutByChoiceValue(item, shortcuts) {
  const shortcutByChoiceValue = new Map();

  if (!item || !shortcuts) {
    return shortcutByChoiceValue;
  }

  const keys = getShortcutKeys(shortcuts);
  let shortcutIndex = 0;

  for (const choice of item.choices ?? []) {
    if (choice.disabled) {
      continue;
    }

    const shortcut = keys[shortcutIndex];

    if (!shortcut) {
      break;
    }

    shortcutByChoiceValue.set(choice.value, shortcut);
    shortcutIndex += 1;
  }

  return shortcutByChoiceValue;
}
