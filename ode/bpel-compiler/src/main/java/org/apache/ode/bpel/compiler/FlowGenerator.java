/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.compiler;

import java.util.Iterator;
import java.util.Stack;

import org.apache.ode.bpel.compiler.api.CompilationException;
import org.apache.ode.bpel.compiler.bom.Activity;
import org.apache.ode.bpel.compiler.bom.CompositeActivity;
import org.apache.ode.bpel.compiler.bom.FlowActivity;
import org.apache.ode.bpel.compiler.bom.Link;
import org.apache.ode.bpel.compiler.modelMigration.ProcessModelChangeRegistry;
import org.apache.ode.bpel.o.OActivity;
import org.apache.ode.bpel.o.OFlow;
import org.apache.ode.bpel.o.OLink;
import org.apache.ode.utils.msg.MessageBundle;
import org.apache.ode.utils.stl.CollectionsX;
import org.apache.ode.utils.stl.MemberOfFunction;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Generates code for <code>&lt;flow&gt;</code> activities.
 */
class FlowGenerator extends DefaultActivityGenerator {
	private static final FlowGeneratorMessages __cmsgs = MessageBundle
			.getMessages(FlowGeneratorMessages.class);

	public void compile(OActivity output, Activity src) {
		FlowActivity flowAct = (FlowActivity) src;
		OFlow oflow = (OFlow) output;
		compileLinkDecls(oflow, flowAct);
		compileChildren(oflow, flowAct);

		for (Iterator<OLink> i = oflow.localLinks.iterator(); i.hasNext();) {
			OLink olink = i.next();
			try {
				if (olink.source == null)
					throw new CompilationException(__cmsgs
							.errLinkMissingSourceActivity(olink.name)
							.setSource(flowAct));
			} catch (CompilationException ce) {
				_context.recoveredFromError(src, ce);
			}

			try {
				if (olink.target == null)
					throw new CompilationException(__cmsgs
							.errLinkMissingTargetActivity(olink.name)
							.setSource(flowAct));
			} catch (CompilationException ce) {
				_context.recoveredFromError(src, ce);
			}
		}
	}

	public OActivity newInstance(Activity src) {
		OFlow flow = null;

		String xpath = BpelCompiler.getXPath(src.getElement());
		if (ProcessModelChangeRegistry.getRegistry().isModelChanged()) {
			int id = ProcessModelChangeRegistry.getRegistry().getCorrectID(
					xpath);
			flow = new OFlow(_context.getOProcess(), _context.getCurrent(), id);
		} else {
			flow = new OFlow(_context.getOProcess(), _context.getCurrent());
		}
		flow.setXpath(xpath);

		return flow;
	}

	private void compileLinkDecls(OFlow oflow, FlowActivity flowAct) {
		for (Link link : flowAct.getLinks()) {
			OLink olink = null;

			// @hahnml
			if (ProcessModelChangeRegistry.getRegistry().isModelChanged()) {
				int id = ProcessModelChangeRegistry.getRegistry().getCorrectID(
						getXPath(link.getElement()));
				olink = new OLink(_context.getOProcess(), id);
			} else {
				olink = new OLink(_context.getOProcess());
			}

			olink.name = link.getLinkName();
			Element elem = link.getElement();
			String location = getXPath(elem);
			olink.setXpath(location);
			declareLink(oflow, olink);
		}
	}

	private void declareLink(final OFlow oflow, final OLink olink)
			throws CompilationException {
		if (CollectionsX.find_if(oflow.localLinks,
				new MemberOfFunction<OLink>() {
					public boolean isMember(OLink o) {
						return o.name.equals(olink.name);
					}
				}) != null)
			throw new CompilationException(
					__cmsgs.errDuplicateLinkDecl(olink.name));

		olink.declaringFlow = oflow;
		oflow.localLinks.add(olink);
	}

	/**
   */
	protected void compileChildren(OFlow dest, CompositeActivity src) {
		for (Activity child : src.getActivities()) {
			try {
				OActivity compiledChild = _context.compile(child);
				dest.parallelActivities.add(compiledChild);
			} catch (CompilationException ce) {
				_context.recoveredFromError(child, ce);
			}
		}
	}

	// @stmz: used to generate a xpath expression for a Node
	public static String getXPath(Node n) {
		// abort early
		if (null == n)
			return null;

		// declarations
		Node parent = null;
		Stack hierarchy = new Stack();
		StringBuffer buffer = new StringBuffer();

		// push element on stack
		hierarchy.push(n);

		parent = n.getParentNode();
		while (null != parent && parent.getNodeType() != Node.DOCUMENT_NODE) {
			// push on stack
			hierarchy.push(parent);

			// get parent of parent
			parent = parent.getParentNode();
		}

		// construct xpath
		Object obj = null;
		while (!hierarchy.isEmpty() && null != (obj = hierarchy.pop())) {
			Node node = (Node) obj;
			boolean handled = false;

			// only consider elements
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) node;

				// is this the root element?
				if (buffer.length() == 0) {
					// root element - simply append element name
					buffer.append(node.getLocalName());
				} else {
					// child element - append slash and element name
					buffer.append("/");
					buffer.append(node.getLocalName());

					if (!handled) {
						int prev_siblings = 1;
						Node prev_sibling = node.getPreviousSibling();
						while (null != prev_sibling) {
							if (prev_sibling.getNodeType() == node
									.getNodeType()) {
								if (prev_sibling.getLocalName()
										.equalsIgnoreCase(node.getLocalName())) {
									prev_siblings++;
								}
							}
							prev_sibling = prev_sibling.getPreviousSibling();
						}
						buffer.append("[" + prev_siblings + "]");
					}
				}
			}
		}

		// return buffer
		String temp = buffer.toString();

		return ("/" + temp);
	}
}
