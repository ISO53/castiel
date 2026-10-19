package io.github.iso53.castiel.tool.webfetch;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** Converts a parsed HTML body to simple markdown: headings, links, lists, code, tables, paragraphs. */
public final class HtmlToMarkdown {

	private HtmlToMarkdown() {}

	/** Entry point: renders the given document body as markdown. */
	public static String convert(Element body) {
		StringBuilder out = new StringBuilder();
		body.childNodes().forEach(child -> appendNode(out, child));
		return out.toString().replaceAll("\\n{3,}", "\n\n").strip();
	}

	private static void appendNode(StringBuilder out, Node node) {
		switch (node) {
			case TextNode text -> out.append(text.text());
			case Element element -> appendElement(out, element);
			case null, default -> {
			}
		}
	}

	private static void appendElement(StringBuilder out, Element element) {
		switch (element.tagName()) {
			case "script", "style", "noscript" -> {
			}
			case "h1", "h2", "h3", "h4", "h5", "h6" -> {
				int level = element.tagName().charAt(1) - '0';
				out.append("\n\n").append("#".repeat(level)).append(' ').append(element.text()).append("\n\n");
			}
			case "p" -> {
				out.append("\n\n");
				element.childNodes().forEach(child -> appendNode(out, child));
				out.append("\n\n");
			}
			case "br" -> out.append('\n');
			case "hr" -> out.append("\n\n---\n\n");
			case "pre" -> out.append("\n\n```\n").append(element.text()).append("\n```\n\n");
			case "blockquote" -> {
				out.append("\n\n");
				element
					.text()
					.lines()
					.forEach(line -> out.append("> ").append(line).append('\n'));
				out.append('\n');
			}
			case "ul", "ol" -> {
				out.append("\n\n");
				int index = 1;
				for (Element item : element.children()) {
					if (!item.tagName().equals("li")) {
						continue;
					}
					out.append(element.tagName().equals("ol") ? index++ + ". " : "- ");
					item.childNodes().forEach(child -> appendNode(out, child));
					out.append('\n');
				}
				out.append('\n');
			}
			case "a" -> {
				String text = element.text();
				if (text.isBlank()) {
					break;
				}
				String href = element.absUrl("href");
				out.append(href.isBlank() ? text : "[" + text + "](" + href + ")");
			}
			case "table" -> appendTable(out, element);
			default -> element.childNodes().forEach(child -> appendNode(out, child));
		}
	}

	/**
	 * Renders a table as a markdown pipe table. Cell text is flattened, pipes are
	 * escaped, and short rows are padded so the grid stays rectangular. Rows of a
	 * nested table are skipped; the nested table collapses into its cell text.
	 */
	private static void appendTable(StringBuilder out, Element table) {
		List<List<String>> grid = new ArrayList<>();
		int columns = 0;
		for (Element row : table.select("tr")) {
			boolean nested = row
				.parents()
				.stream()
				.anyMatch(parent -> parent.tagName().equals("table") && parent != table);
			if (nested) {
				continue;
			}
			List<String> cells = new ArrayList<>();
			for (Element cell : row.select("> td, > th")) {
				cells.add(cell.text().replace("|", "\\|").strip());
			}
			if (cells.isEmpty()) {
				continue;
			}
			columns = Math.max(columns, cells.size());
			grid.add(cells);
		}
		if (grid.isEmpty() || columns == 0) {
			return;
		}
		out.append("\n\n");
		for (int row = 0; row < grid.size(); row++) {
			out.append('|');
			for (int column = 0; column < columns; column++) {
				out.append(' ')
					.append(column < grid.get(row).size() ? grid.get(row).get(column) : "")
					.append(" |");
			}
			out.append('\n');
			if (row == 0) {
				out.append('|').append(" --- |".repeat(columns)).append('\n');
			}
		}
	}
}
