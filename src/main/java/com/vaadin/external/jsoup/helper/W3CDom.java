package com.vaadin.external.jsoup.helper;

import com.vaadin.external.jsoup.nodes.Attribute;
import com.vaadin.external.jsoup.nodes.Attributes;
import com.vaadin.external.jsoup.select.NodeTraversor;
import com.vaadin.external.jsoup.select.NodeVisitor;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * Helper class to transform a {@link com.vaadin.external.jsoup.nodes.Document} to a {@link org.w3c.dom.Document org.w3c.dom.Document},
 * for integration with toolsets that use the W3C DOM.
 * <p>
 * This class is currently <b>experimental</b>, please provide feedback on utility and any problems experienced.
 * </p>
 */
public class W3CDom {
    protected DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    /**
     * Convert a jsoup Document to a W3C Document.
     * @param in jsoup doc
     * @return w3c doc
     */
    public Document fromJsoup(com.vaadin.external.jsoup.nodes.Document in) {
        Validate.notNull(in);
        DocumentBuilder builder;
        try {
        	//set the factory to be namespace-aware
        	factory.setNamespaceAware(true);
            builder = factory.newDocumentBuilder();
            Document out = builder.newDocument();
            convert(in, out);
            return out;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Converts a jsoup document into the provided W3C Document. If required, you can set options on the output document
     * before converting.
     * @param in jsoup doc
     * @param out w3c doc
     * @see com.vaadin.external.jsoup.helper.W3CDom#fromJsoup(com.vaadin.external.jsoup.nodes.Document)
     */
    public void convert(com.vaadin.external.jsoup.nodes.Document in, Document out) {
        if (!StringUtil.isBlank(in.location()))
            out.setDocumentURI(in.location());

        com.vaadin.external.jsoup.nodes.Element rootEl = in.child(0); // skip the #root node
        NodeTraversor traversor = new NodeTraversor(new W3CBuilder(out));
        traversor.traverse(rootEl);
    }

    /**
     * Implements the conversion by walking the input.
     */
    protected static class W3CBuilder implements NodeVisitor {
        private static final String xmlnsKey = "xmlns";
        private static final String xmlnsPrefix = "xmlns:";

        private final Document doc;
        private final HashMap<String, String> namespaces = new HashMap<String, String>(); // prefix => urn
        private Element dest;

        public W3CBuilder(Document doc) {
            this.doc = doc;
        }

        public void head(com.vaadin.external.jsoup.nodes.Node source, int depth) {
            if (source instanceof com.vaadin.external.jsoup.nodes.Element) {
                com.vaadin.external.jsoup.nodes.Element sourceEl = (com.vaadin.external.jsoup.nodes.Element) source;

                String prefix = updateNamespaces(sourceEl);
                String namespace = namespaces.get(prefix);

                Element el = doc.createElementNS(namespace, sourceEl.tagName());
                copyAttributes(sourceEl, el);
                if (dest == null) { // sets up the root
                    doc.appendChild(el);
                } else {
                    dest.appendChild(el);
                }
                dest = el; // descend
            } else if (source instanceof com.vaadin.external.jsoup.nodes.TextNode) {
                com.vaadin.external.jsoup.nodes.TextNode sourceText = (com.vaadin.external.jsoup.nodes.TextNode) source;
                Text text = doc.createTextNode(sourceText.getWholeText());
                dest.appendChild(text);
            } else if (source instanceof com.vaadin.external.jsoup.nodes.Comment) {
                com.vaadin.external.jsoup.nodes.Comment sourceComment = (com.vaadin.external.jsoup.nodes.Comment) source;
                Comment comment = doc.createComment(sourceComment.getData());
                dest.appendChild(comment);
            } else if (source instanceof com.vaadin.external.jsoup.nodes.DataNode) {
                com.vaadin.external.jsoup.nodes.DataNode sourceData = (com.vaadin.external.jsoup.nodes.DataNode) source;
                Text node = doc.createTextNode(sourceData.getWholeData());
                dest.appendChild(node);
            } else {
                // unhandled
            }
        }

        public void tail(com.vaadin.external.jsoup.nodes.Node source, int depth) {
            if (source instanceof com.vaadin.external.jsoup.nodes.Element && dest.getParentNode() instanceof Element) {
                dest = (Element) dest.getParentNode(); // undescend. cromulent.
            }
        }

        private void copyAttributes(com.vaadin.external.jsoup.nodes.Node source, Element el) {
            for (Attribute attribute : source.attributes()) {
                el.setAttribute(attribute.getKey(), attribute.getValue());
            }
        }

        /**
         * Finds any namespaces defined in this element. Returns any tag prefix.
         */
        private String updateNamespaces(com.vaadin.external.jsoup.nodes.Element el) {
            // scan the element for namespace declarations
            // like: xmlns="blah" or xmlns:prefix="blah"
            Attributes attributes = el.attributes();
            for (Attribute attr : attributes) {
                String key = attr.getKey();
                String prefix;
                if (key.equals(xmlnsKey)) {
                    prefix = "";
                } else if (key.startsWith(xmlnsPrefix)) {
                    prefix = key.substring(xmlnsPrefix.length());
                } else {
                    continue;
                }
                namespaces.put(prefix, attr.getValue());
            }

            // get the element prefix if any
            int pos = el.tagName().indexOf(":");
            return pos > 0 ? el.tagName().substring(0, pos) : "";
        }

    }

    /**
     * Serialize a W3C document to a String.
     * @param doc Document
     * @return Document as string
     */
    public String asString(Document doc) {
        try {
            DOMSource domSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }
}
