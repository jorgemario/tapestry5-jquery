// Copyright 2007, 2008, 2009, 2010, 2011 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.got5.tapestry5.jquery.components;

import static org.apache.tapestry5.commons.util.CollectionFactory.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tapestry5.Asset;
import org.apache.tapestry5.Binding;
import org.apache.tapestry5.BindingConstants;
import org.apache.tapestry5.Block;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.FieldValidationSupport;
import org.apache.tapestry5.FieldValidator;
import org.apache.tapestry5.MarkupWriter;
import org.apache.tapestry5.OptionGroupModel;
import org.apache.tapestry5.OptionModel;
import org.apache.tapestry5.Renderable;
import org.apache.tapestry5.SelectModel;
import org.apache.tapestry5.SelectModelVisitor;
import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.ValidationException;
import org.apache.tapestry5.ValidationTracker;
import org.apache.tapestry5.ValueEncoder;
import org.apache.tapestry5.annotations.Environmental;
import org.apache.tapestry5.annotations.Import;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.annotations.Property;
import org.apache.tapestry5.corelib.base.AbstractField;
import org.apache.tapestry5.corelib.components.Checklist;
import org.apache.tapestry5.corelib.components.Form;
import org.apache.tapestry5.corelib.components.Select;
import org.apache.tapestry5.internal.util.SelectModelRenderer;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.commons.util.CollectionFactory;
import org.apache.tapestry5.json.JSONArray;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.ComponentDefaultProvider;
import org.apache.tapestry5.http.services.Request;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;

/**
 * <p>
 * Multiple selection component. Generates a UI consisting of two &lt;select&gt; elements configured for multiple
 * selection; the one on the left is the list of "available" elements, the one on the right is "selected". Elements can
 * be moved between the lists by clicking a button, or double clicking an option (and eventually, via drag and drop).
 * </p>
 * <p>
 * The items in the available list are kept ordered as per {@link SelectModel} order. When items are moved from the
 * selected list to the available list, they items are inserted back into their proper positions.
 * </p>
 * <p>
 * The Palette may operate in normal or re-orderable mode, controlled by the reorder parameter.
 * </p>
 * <p>
 * In normal mode, the items in the selected list are kept in the same "natural" order as the items in the available
 * list.
 * </p>
 * <p>
 * In re-order mode, items moved to the selected list are simply added to the bottom of the list. In addition, two extra
 * buttons appear to move items up and down within the selected list.
 * </p>
 * <p>
 * Much of the look and feel is driven by CSS, the default Tapestry CSS is used to set up the columns, etc. By default,
 * the &lt;select&gt; element's widths are 200px, and it is common to override this to a specific value:
 * </p>
 * <pre>
 * &lt;style&gt;
 * DIV.t-palette SELECT { width: 300px; }
 * &lt;/style&gt;
 * </pre>
 * <p>
 * You'll want to ensure that both &lt;select&gt; in each column is the same width, otherwise the display will update
 * poorly as options are moved from one column to the other.
 * </p>
 * <p>
 * Option groups within the {@link SelectModel} will be rendered, but are not supported by many browsers, and are not
 * fully handled on the client side.
 * </p>
 * <p>
 * For an alternative component that can be used for similar purposes, see {@link Checklist}.
 * </p>
 *
 * @see Form
 * @see Select
 * /@tapestrydoc
 */
@Import(stylesheet = "classpath:/META-INF/assets/core/Palette.css")
public class Palette extends AbstractField
{
    // These all started as anonymous inner classes, and were refactored out to here.
    // I was chasing down one of those perplexing bytecode errors.

    private final class AvailableRenderer implements Renderable
    {
        public void render(MarkupWriter writer)
        {
            writer.element("select", "id", getClientId() + "-avail", "multiple", "multiple", "size", getSize(), "name",
                    getControlName() + "-avail");

            writeDisabled(writer, isDisabled());

            for (Runnable r : availableOptions)
                r.run();

            writer.end();
        }
    }

    private final class OptionGroupEnd implements Runnable
    {
        private final OptionGroupModel model;

        private OptionGroupEnd(OptionGroupModel model)
        {
            this.model = model;
        }

        public void run()
        {
            renderer.endOptionGroup(model);
        }
    }

    private final class OptionGroupStart implements Runnable
    {
        private final OptionGroupModel model;

        private OptionGroupStart(OptionGroupModel model)
        {
            this.model = model;
        }

        public void run()
        {
            renderer.beginOptionGroup(model);
        }
    }

    private final class RenderOption implements Runnable
    {
        private final OptionModel model;

        private RenderOption(OptionModel model)
        {
            this.model = model;
        }

        public void run()
        {
            renderer.option(model);
        }
    }

    private final class SelectedRenderer implements Renderable
    {
        public void render(MarkupWriter writer)
        {
            writer.element("select", "id", getClientId(), "multiple", "multiple", "size", getSize(), "name",
                    getControlName());

            writeDisabled(writer, isDisabled());

            putPropertyNameIntoBeanValidationContext("selected");

            Palette.this.validate.render(writer);

            removePropertyNameFromBeanValidationContext();

            for (Object value : getSelected())
            {
                OptionModel model = valueToOptionModel.get(value);

                renderer.option(model);
            }

            writer.end();
        }
    }

    /**
     * List of Runnable commands to render the available options.
     */
    private List<Runnable> availableOptions;

    /**
     * The image to use for the deselect button (the default is a left pointing arrow).
     */
	@Parameter(value = "asset:deselect.png")
    @Property(write = false)
    private Asset deselect;

    /**
     * A ValueEncoder used to convert server-side objects (provided from the
     * "source" parameter) into unique client-side strings (typically IDs) and
     * back. Note: this component does NOT support ValueEncoders configured to
     * be provided automatically by Tapestry.
     */
    @Parameter(required = true, allowNull = false)
    private ValueEncoder<Object> encoder;

    /**
     * Model used to define the values and labels used when rendering.
     */
    @Parameter(required = true, allowNull = false)
    private SelectModel model;

    /**
     * Allows the title text for the available column (on the left) to be modified. As this is a Block, it can contain
     * conditionals and components. The default is the text "Available".
     */
    @Property(write = false)
    @Parameter(required = true, allowNull = false, value = "message:available-label", defaultPrefix = BindingConstants.LITERAL)
    private Block availableLabel;

    /**
     * Allows the title text for the selected column (on the right) to be modified. As this is a Block, it can contain
     * conditionals and components. The default is the text "Available".
     */
    @Property(write = false)
    @Parameter(required = true, allowNull = false, value = "message:selected-label", defaultPrefix = BindingConstants.LITERAL)
    private Block selectedLabel;

    /**
     * The image to use for the move down button (the default is a downward pointing arrow).
     */
    @Parameter(value = "asset:move_down.png")
    @Property(write = false)
    private Asset moveDown;

    /**
     * The image to use for the move up button (the default is an upward pointing arrow).
     */
    @Parameter(value = "asset:move_up.png")
    @Property(write = false)
    private Asset moveUp;

    /**
     * Used to include scripting code in the rendered page.
     */
    @Environmental
    private JavaScriptSupport javascriptSupport;

    @Environmental
    private ValidationTracker tracker;

    /**
     * Needed to access query parameters when processing form submission.
     */
    @Inject
    private Request request;

    @Inject
    private ComponentDefaultProvider defaultProvider;

    @Inject
    private ComponentResources componentResources;

    @Inject
    private FieldValidationSupport fieldValidationSupport;

    private SelectModelRenderer renderer;

    /**
     * The image to use for the select button (the default is a right pointing arrow).
     */
    @Parameter(value = "asset:select.png")
    @Property(write = false)
    private Asset select;

    /**
     * The list of selected values from the {@link org.apache.tapestry5.SelectModel}. This will be updated when the form
     * is submitted. If the value for the parameter is null, a new list will be created, otherwise the existing list
     * will be cleared. If unbound, defaults to a property of the container matching this component's id.
     */
    @Parameter(required = true, autoconnect = true)
    private List<Object> selected;

    /**
     * If true, then additional buttons are provided on the client-side to allow for re-ordering of the values.
     */
    @Parameter("false")
    @Property(write = false)
    private boolean reorder;

    /**
     * Used during rendering to identify the options corresponding to selected values (from the selected parameter), in
     * the order they should be displayed on the page.
     */
    private List<OptionModel> selectedOptions;

    private Map<Object, OptionModel> valueToOptionModel;

    /**
     * Number of rows to display.
     */
    @Parameter(value = "10")
    private int size;

    /**
     * The object that will perform input validation. The validate binding prefix is generally used to provide
     * this object in a declarative fashion.
     *
     * @since 5.2.0
     */
    @Parameter(defaultPrefix = BindingConstants.VALIDATE)
    @SuppressWarnings("unchecked")
    private FieldValidator<Object> validate;

    @Inject
    @Symbol(SymbolConstants.COMPACT_JSON)
    private boolean compactJSON;

    /**
     * The natural order of elements, in terms of their client ids.
     */
    private List<String> naturalOrder;

    public Renderable getAvailableRenderer()
    {
        return new AvailableRenderer();
    }

    public Renderable getSelectedRenderer()
    {
        return new SelectedRenderer();
    }

    @Override
    protected void processSubmission(String controlName)
    {
        String parameterValue = request.getParameter(controlName + "-values");

        this.tracker.recordInput(this, parameterValue);

        JSONArray values = new JSONArray(parameterValue);

        // Use a couple of local variables to cut down on access via bindings

        List<Object> selected = this.selected;

        if (selected == null)
            selected = newList();
        else
            selected.clear();

        ValueEncoder encoder = this.encoder;

        int count = values.length();
        for (int i = 0; i < count; i++)
        {
            String value = values.getString(i);

            Object objectValue = encoder.toValue(value);

            selected.add(objectValue);
        }

        putPropertyNameIntoBeanValidationContext("selected");

        try
        {
            this.fieldValidationSupport.validate(selected, this.componentResources, this.validate);

            this.selected = selected;
        } catch (final ValidationException e)
        {
            this.tracker.recordError(this, e.getMessage());
        }

        removePropertyNameFromBeanValidationContext();
    }

    private void writeDisabled(MarkupWriter writer, boolean disabled)
    {
        if (disabled)
            writer.attributes("disabled", "disabled");
    }

    void beginRender(MarkupWriter writer)
    {
        JSONArray selectedValues = new JSONArray();

        for (OptionModel selected : selectedOptions)
        {

            Object value = selected.getValue();
            String clientValue = encoder.toClient(value);

            selectedValues.put(clientValue);
        }

        JSONArray naturalOrder = new JSONArray();

        for (String value : this.naturalOrder)
        {
            naturalOrder.put(value);
        }

        String clientId = getClientId();




        JSONObject options = new JSONObject();
        options.put("id", clientId);
        options.put("reorder", reorder);
        options.put("naturalOrder", naturalOrder);

        javascriptSupport.require("tjq/palette").with(options);

        writer.element("input", "type", "hidden", "id", clientId + "-values", "name", getControlName() + "-values",
                "value", selectedValues);
        writer.end();
    }

    /**
     * Prevent the body from rendering.
     */
    boolean beforeRenderBody()
    {
        return false;
    }

    void setupRender(MarkupWriter writer)
    {
        valueToOptionModel = CollectionFactory.newMap();
        availableOptions = CollectionFactory.newList();
        selectedOptions = CollectionFactory.newList();
        naturalOrder = CollectionFactory.newList();
        renderer = new SelectModelRenderer(writer, encoder, compactJSON);

        @SuppressWarnings("rawtypes")
		final Set selectedSet = newSet(getSelected());

        SelectModelVisitor visitor = new SelectModelVisitor()
        {
            public void beginOptionGroup(OptionGroupModel groupModel)
            {
                availableOptions.add(new OptionGroupStart(groupModel));
            }

            public void endOptionGroup(OptionGroupModel groupModel)
            {
                availableOptions.add(new OptionGroupEnd(groupModel));
            }

            public void option(OptionModel optionModel)
            {
                Object value = optionModel.getValue();

                boolean isSelected = selectedSet.contains(value);

                String clientValue = toClient(value);

                naturalOrder.add(clientValue);

                if (isSelected)
                {
                    valueToOptionModel.put(value, optionModel);
                    return;
                }

                availableOptions.add(new RenderOption(optionModel));
            }
        };

        model.visit(visitor);
        //adding selectedOptions in ordering according to getSelected()
		for (Object selected : getSelected())
		    selectedOptions.add(valueToOptionModel.get(selected));
    }

    /**
     * Computes a default value for the "validate" parameter using
     * {@link org.apache.tapestry5.services.FieldValidatorDefaultSource}.
     */
    Binding defaultValidate()
    {
        return this.defaultProvider.defaultValidatorBinding("selected", this.componentResources);
    }

    // Avoids a strange Javassist bytecode error, c'est lavie!
    int getSize()
    {
        return size;
    }

    String toClient(Object value)
    {
        return encoder.toClient(value);
    }

    List<Object> getSelected()
    {
        if (selected == null)
            return Collections.emptyList();

        return selected;
    }

    @Override
    public boolean isRequired()
    {
        return validate.isRequired();
    }
}
