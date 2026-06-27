export function replaceChildrenSafe(node, children) {
  if (!node) return;
  node.replaceChildren(...children);
}

export function makeElement(tag, className = "", text = "") {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text) element.textContent = text;
  return element;
}

export function fragmentFromHtml(markup = "") {
  return document.createRange().createContextualFragment(String(markup || ""));
}
