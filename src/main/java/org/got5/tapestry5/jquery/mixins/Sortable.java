package org.got5.tapestry5.jquery.mixins;

import org.apache.tapestry5.ClientElement;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.annotations.AfterRender;
import org.apache.tapestry5.annotations.Events;
import org.apache.tapestry5.annotations.InjectContainer;
import org.apache.tapestry5.annotations.OnEvent;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.http.services.Request;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;
import org.got5.tapestry5.jquery.JQueryEventConstants;

/**
 * Sortable mixin, most of the time used with a UL HTML tag
 * @since 3.2.0
 * @see <a href="http://jqueryui.com/sortable/">jQuery UI Official Documentation</a>
 * @author Emmanuel DEMEY
 * 
 * /@tapestrydoc
 */
@Events(JQueryEventConstants.SORTABLE)
public class Sortable {
	
	/**
	 * JSONObject-type parameter, used by the Sortable JQuery-UI widget
	 */
	@Parameter
	private JSONObject options;
	
	@Inject
    private JavaScriptSupport javaScriptSupport;
	
	@Inject
    private ComponentResources resources;
	
	@Inject
	private Request request;
	
	@InjectContainer
	private ClientElement clientElement;
	
	@AfterRender
	public void initJs(){
		if(options == null) options = new JSONObject();
		JSONObject opt = new JSONObject();
		opt.put("id", clientElement.getClientId());
		opt.put("url" , resources.createEventLink("sortable", (Object[]) null).toAbsoluteURI());
		opt.put("params", options);
		javaScriptSupport.require("tjq/ui").invoke("sortable").with(opt);
	}
	
	@OnEvent("sortable")	
	public void sort(){
		
		String value = request.getParameter("list");
		resources.triggerEvent(JQueryEventConstants.SORTABLE, new Object[] {value}, null);
	}
}
