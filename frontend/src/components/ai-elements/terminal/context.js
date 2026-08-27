import { inject } from "vue";

// Shared injection key + typed accessor for the Terminal component family.
export const TerminalKey = Symbol("Terminal");

export function useTerminalContext(componentName) {
	const context = inject(TerminalKey);
	if (!context) {
		throw new Error(`${componentName} must be used within Terminal`);
	}
	return context;
}
