package org.got5.tapestry5.jquery.mixins;

import org.apache.tapestry5.BindingConstants;
import org.apache.tapestry5.ClientElement;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.annotations.AfterRender;
import org.apache.tapestry5.annotations.InjectContainer;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.javascript.InitializationPriority;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;
import org.got5.tapestry5.jquery.services.messages.MessageProvider;
import org.got5.tapestry5.jquery.utils.JQueryUtils;

/**
 * A mixin used to attach a JavaScript confirmation box to the onclick
 * event of any component that implements ClientElement.
 * /@tapestrydoc
 */
public class Confirm
{
	/**
	 * Confirmation message to display.
	 */
	@Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String message;

	/**
	 * Dialog box title.
	 */
	@Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String title;

	/**
	 * Validation Button text.
	 */
	@Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String validationMsg;

	/**
	 * Cancel Button text.
	 */
	@Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String cancelMsg;

	/**
	 * Confirmation box height.
	 */
	@Parameter(value = "180", defaultPrefix = BindingConstants.LITERAL)
	private int height;

	/**
	 * Confirmation box width.
	 */
	@Parameter(value = "250", defaultPrefix = BindingConstants.LITERAL)
	private int width;
	/**
	 * If this parameter is set to <i>true</i>, the user can't interact with the application while the
	 * confirmation box is displayed.
	 */
	@Parameter(value = "true", defaultPrefix = BindingConstants.LITERAL)
	private boolean isModal;

	/**
	 * If this parameter is set to <i>true</i>, the user can dynamically resize the confirmation box.
	 */
	@Parameter(value = "false", defaultPrefix = BindingConstants.LITERAL)
	private boolean isResizable;

	/**
	 * If this parameter is set to <i>true</i>, the user can drag the confirmation box.
	 */
	@Parameter(value = "true", defaultPrefix = BindingConstants.LITERAL)
	private boolean isDraggable;

	/**
	 * If this parameter is set to <i>true</i>, default javascript alert is used. Otherwise, jquery dialog box
	 * is used, and can be customized with several parameters.
	 *
	 * Usable parameters in both configurations :
	 * message
	 *
	 * Usable parameters when useDefaultConfirm = false:
	 * title, validationMsg, cancelMsg, isModal, isResizable, isDraggable
	 *
	 * validationMsg, cancelMsg, isModal, isResizable, height
	 */
	@Parameter(value = "false", defaultPrefix = BindingConstants.LITERAL)
	private boolean useDefaultConfirm;


	/**
	 * since 3.4.3
	 * The Confirm parameters you want to override.
	 *
	 * This will be used only if seDefaultConfirm = false (defaultValue):
	 */
	@Parameter
	private JSONObject params;

	//Injected services.

    @Inject
    private JavaScriptSupport javaScriptSupport;

    @InjectContainer
    private ClientElement element;

    @Inject
    private ComponentResources resources;

    @Inject
    private MessageProvider messageProvider;

    @AfterRender
    public void afterRender()
    {
    	JSONObject config = new JSONObject();

    	String clientId = element.getClientId();

    	config.put("id", clientId);

    	config.put("title", title != null ? title : messageProvider.get("default-confirm-title", clientId, resources));
    	config.put("message", message != null ? message : messageProvider.get("default-confirm-message", clientId, resources));
    	config.put("validationMsg", validationMsg != null ? validationMsg : messageProvider.get("default-valid-label", clientId, resources));
    	config.put("cancelMsg", cancelMsg != null ? cancelMsg : messageProvider.get("default-cancel-label", clientId, resources));

    	config.put("useDefaultConfirm", useDefaultConfirm);
    	config.put("isModal", isModal);
    	config.put("isResizable", isResizable);
    	config.put("isDraggable", isDraggable);
    	config.put("height", height);
    	config.put("width", width);

    	/*
    	 * We will merge the default JSON Object with the params parameter
    	 */
    	if (resources.isBound("params"))
    	{
    		JQueryUtils.merge(config, params);
    	}


        javaScriptSupport.require("tjq/confirm").priority(InitializationPriority.EARLY).with(config);

    }

}
