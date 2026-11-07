package br.com.lhamalog.core.company;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import br.com.lhamalog.core.shared.FieldValidationException;

final class LogoFiles {
	static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final Set<String> BLOCKED = Set.of("script", "foreignobject", "iframe", "embed", "object", "audio",
			"video", "animate", "set", "handler", "image", "style", "link", "meta", "canvas");

	record Stored(String fileName, String contentType, byte[] content) {}

	private LogoFiles() {}

	static void verifyAccepted(byte[] content, String mediaType) {
		if (content == null || content.length == 0 || content.length > MAX_BYTES) {
			throw new FieldValidationException(java.util.Map.of("settings.data.logo",
					content != null && content.length > MAX_BYTES ? "O logotipo excede 2 MB."
							: "Envie um arquivo PNG, JPG ou SVG de até 2 MB."));
		}
		boolean png = isPng(content);
		boolean jpeg = isJpeg(content);
		boolean svg = looksLikeSvg(content);
		if ("image/png".equals(mediaType) && png) {
			return;
		}
		if (("image/jpeg".equals(mediaType) || "image/jpg".equals(mediaType)) && jpeg) {
			return;
		}
		if ("image/svg+xml".equals(mediaType) && svg && svgIsSafe(content)) {
			return;
		}
		throw new FieldValidationException(java.util.Map.of("settings.data.logo",
				svg ? "O SVG não pôde ser aceito com segurança." : "Envie um arquivo PNG, JPG ou SVG."));
	}

	private static boolean svgIsSafe(byte[] content) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
			Element root = document.getDocumentElement();
			if (root == null || !"svg".equals(localName(root))) {
				return false;
			}
			return safeTree(root, new int[] {0});
		}
		catch (Exception exception) {
			return false;
		}
	}

	private static boolean safeTree(Element element, int[] count) {
		count[0]++;
		if (count[0] > 2000 || BLOCKED.contains(localName(element))) {
			return false;
		}
		NamedNodeMap attributes = element.getAttributes();
		for (int index = 0; index < attributes.getLength(); index++) {
			Node attribute = attributes.item(index);
			String name = localName(attribute);
			String value = attribute.getNodeValue() == null ? "" : attribute.getNodeValue().trim();
			if (name.startsWith("on") || unsafeReference(name, value) || unsafeStyle(name, value)) {
				return false;
			}
		}
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element childElement && !safeTree(childElement, count)) {
				return false;
			}
		}
		return true;
	}

	static Stored inspect(byte[] content, String originalName) {
		if (content == null || content.length == 0) {
			throw new FieldValidationException(java.util.Map.of("logo", "Envie um arquivo PNG, JPG ou SVG de até 2 MB."));
		}
		if (content.length > MAX_BYTES) {
			throw new FieldValidationException(java.util.Map.of("logo", "O logotipo excede 2 MB."));
		}
		if (isPng(content)) {
			return new Stored(name(originalName, "png"), "image/png", content);
		}
		if (isJpeg(content)) {
			return new Stored(name(originalName, "jpg"), "image/jpeg", content);
		}
		if (looksLikeSvg(content)) {
			byte[] sanitized = sanitizeSvg(content);
			if (sanitized.length > MAX_BYTES) {
				throw new FieldValidationException(java.util.Map.of("logo", "O logotipo excede 2 MB."));
			}
			return new Stored(name(originalName, "svg"), "image/svg+xml", sanitized);
		}
		throw new FieldValidationException(java.util.Map.of("logo", "Envie um arquivo PNG, JPG ou SVG."));
	}

	private static boolean isPng(byte[] content) {
		return content.length >= 8 && content[0] == (byte) 0x89 && content[1] == 0x50 && content[2] == 0x4E
				&& content[3] == 0x47;
	}

	private static boolean isJpeg(byte[] content) {
		return content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
				&& (content[2] & 0xFF) == 0xFF;
	}

	private static boolean looksLikeSvg(byte[] content) {
		String start = new String(content, 0, Math.min(content.length, 256), StandardCharsets.UTF_8).trim()
				.toLowerCase(Locale.ROOT);
		return start.startsWith("<svg") || start.startsWith("<?xml");
	}

	private static byte[] sanitizeSvg(byte[] content) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
			Element root = document.getDocumentElement();
			if (root == null || !"svg".equals(localName(root))) {
				throw rejected();
			}
			int[] count = {0};
			clean(root, count);
			if (count[0] > 2000) {
				throw rejected();
			}
			TransformerFactory transformers = TransformerFactory.newInstance();
			transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			var transformer = transformers.newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			transformer.transform(new DOMSource(document), new StreamResult(output));
			byte[] sanitized = output.toByteArray();
			String text = new String(sanitized, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
			if (!text.contains("<svg") || text.contains("<script") || text.contains("javascript:")) {
				throw rejected();
			}
			return sanitized;
		}
		catch (FieldValidationException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw rejected();
		}
	}

	private static void clean(Element element, int[] count) {
		count[0]++;
		NamedNodeMap attributes = element.getAttributes();
		List<String> drop = new ArrayList<>();
		for (int index = 0; index < attributes.getLength(); index++) {
			Node attribute = attributes.item(index);
			String name = localName(attribute);
			String value = attribute.getNodeValue() == null ? "" : attribute.getNodeValue().trim();
			if (name.startsWith("on") || unsafeReference(name, value) || unsafeStyle(name, value)) {
				drop.add(attribute.getNodeName());
			}
		}
		drop.forEach(element::removeAttribute);
		List<Element> blocked = new ArrayList<>();
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element childElement) {
				if (BLOCKED.contains(localName(childElement))) {
					blocked.add(childElement);
				}
				else {
					clean(childElement, count);
				}
			}
		}
		blocked.forEach(element::removeChild);
	}

	private static boolean unsafeReference(String name, String value) {
		if (!name.equals("href") && !name.endsWith("href")) {
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		return !normalized.startsWith("#") || normalized.contains("javascript:") || normalized.contains("data:");
	}

	private static boolean unsafeStyle(String name, String value) {
		if (!name.equals("style")) {
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		return normalized.contains("javascript:") || normalized.contains("expression(") || normalized.contains("url(")
				|| normalized.contains("@import");
	}

	private static String localName(Node node) {
		String value = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
		int colon = value.indexOf(':');
		return (colon >= 0 ? value.substring(colon + 1) : value).toLowerCase(Locale.ROOT);
	}

	private static String name(String original, String extension) {
		String base = original == null ? "logo" : original.replace('\\', '/');
		int slash = base.lastIndexOf('/');
		base = slash >= 0 ? base.substring(slash + 1) : base;
		base = base.replaceAll("[^A-Za-z0-9._-]", "_");
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		if (base.isBlank() || base.length() > 60) {
			base = "logo";
		}
		return base + "." + extension;
	}

	private static FieldValidationException rejected() {
		return new FieldValidationException(java.util.Map.of("logo", "O SVG não pôde ser aceito com segurança."));
	}
}
