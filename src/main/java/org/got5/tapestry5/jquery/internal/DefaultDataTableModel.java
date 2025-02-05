package org.got5.tapestry5.jquery.internal;

import org.apache.tapestry5.Block;
import org.apache.tapestry5.MarkupWriter;
import org.apache.tapestry5.PropertyOverrides;
import org.apache.tapestry5.Translator;
import org.apache.tapestry5.beanmodel.BeanModel;
import org.apache.tapestry5.beanmodel.PropertyConduit;
import org.apache.tapestry5.commons.services.TypeCoercer;
import org.apache.tapestry5.dom.Element;
import org.apache.tapestry5.grid.ColumnSort;
import org.apache.tapestry5.grid.GridDataSource;
import org.apache.tapestry5.grid.GridSortModel;
import org.apache.tapestry5.grid.SortConstraint;
import org.apache.tapestry5.http.services.Request;
import org.apache.tapestry5.internal.grid.CollectionGridDataSource;
import org.apache.tapestry5.internal.services.AjaxPartialResponseRenderer;
import org.apache.tapestry5.internal.services.PageRenderQueue;
import org.apache.tapestry5.internal.services.ajax.AjaxFormUpdateController;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.json.JSONArray;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.runtime.RenderCommand;
import org.apache.tapestry5.runtime.RenderQueue;
import org.apache.tapestry5.services.PartialMarkupRenderer;
import org.apache.tapestry5.services.PartialMarkupRendererFilter;
import org.apache.tapestry5.services.TranslatorSource;
import org.got5.tapestry5.jquery.DataTableConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A default DataTable model that handles ajax mode.
 * Used for lazy loading and server-side pagination
 */
public class DefaultDataTableModel implements DataTableModel {

    public class CustomGridDataSource implements GridDataSource {

        private List<Object> datas;
        private CollectionGridDataSource cgds;

        public CustomGridDataSource(List<Object> datas) {
            super();
            this.datas = datas;
            this.cgds =  new CollectionGridDataSource(datas);
        }

        public CustomGridDataSource(GridDataSource gds) {
            super();

            this.datas = new ArrayList<Object>();


            for(int i = 0; i < gds.getAvailableRows(); i++){
                datas.add(gds.getRowValue(i));
            }


            this.cgds =  new CollectionGridDataSource(datas);
        }

        public void prepare(int startIndex, int endIndex,
                List<SortConstraint> sortConstraints) {

            for (SortConstraint constraint : sortConstraints)
            {
                final ColumnSort sort = constraint.getColumnSort();

                String name = constraint.getPropertyModel().getPropertyName();

                final PropertyConduit conduit = model.get(name).getConduit();

                if(sort == ColumnSort.UNSORTED || conduit == null)
                    continue;

                final Comparator valueComparator = new Comparator<Comparable>()
                {
                    public int compare(Comparable o1, Comparable o2)
                    {
                        // Simplify comparison, and handle case where both are nulls.

                        if (o1 == o2)
                            return 0;

                        if (o2 == null)
                            return 1;

                        if (o1 == null)
                            return -1;

                        return o1.compareTo(o2);
                    }
                };

                final Comparator rowComparator = new Comparator()
                {
                    public int compare(Object row1, Object row2)
                    {
                        Comparable value1 = (Comparable) conduit.get(row1);
                        Comparable value2 = (Comparable) conduit.get(row2);

                        return valueComparator.compare(value1, value2);
                    }
                };

                final Comparator reverseComparator = new Comparator()
                {
                    public int compare(Object o1, Object o2)
                    {
                        int modifier = sort == ColumnSort.ASCENDING ? 1 : -1;

                        return modifier * rowComparator.compare(o1, o2);
                    }
                };

                // We can freely sort this list because its just a copy.
                Collections.sort(datas, reverseComparator);
                cgds = new CollectionGridDataSource(datas);
            }
        }

        public Object getRowValue(int index) {
            return this.cgds.getRowValue(index);
        }

        public Class getRowType() {
            return this.cgds .getRowType();
        }


        public int getAvailableRows() {
            return this.cgds.getAvailableRows();
        }
    };

    private TypeCoercer typeCoercer;

    private Request request;

    private GridSortModel sortModel;

    private BeanModel model;

    private PropertyOverrides overrides;

    private TranslatorSource translatorSource;

    private PageRenderQueue pageRenderQueue;

    private AjaxFormUpdateController ajaxFormUpdateController;

    private AjaxPartialResponseRenderer partialRenderer;

    private JSONObject response;

    /**
     * The JSONArray object that stores the datatable rows rendered by ajax
     */
    JSONArray rows;

    private final FakeInheritedBinding rowParam;
    private final FakeInheritedBinding rowIndexParam;

    public DefaultDataTableModel(
            TypeCoercer typeCoercer,
            TranslatorSource translatorSource,
            PageRenderQueue pageRenderQueue,
            AjaxFormUpdateController ajaxFormUpdateController,
            AjaxPartialResponseRenderer partialRenderer,
            FakeInheritedBinding row, FakeInheritedBinding rowIndex) {
        super();
        this.typeCoercer = typeCoercer;
        this.translatorSource = translatorSource;
        this.pageRenderQueue = pageRenderQueue;
        this.ajaxFormUpdateController = ajaxFormUpdateController;
        this.partialRenderer = partialRenderer;
        this.rowParam = row;
        this.rowIndexParam = rowIndex;
        response = new JSONObject();
        rows = new JSONArray() ;
    }

    /**
     * This method will filter all your data by using the search input from your datatable.
     *
     * @param source any grid data source
     * @return a filtered data source
     */
    public GridDataSource filterData(GridDataSource source){

        final List<Object> datas = new ArrayList<Object>();

        for(int index=0;index<source.getAvailableRows();index++){

            boolean flag = false;

            for (Object name: model.getPropertyNames())
            {
                PropertyConduit conduit = model.get((String) name).getConduit();

                try{

                    String val = (String) conduit.get(source.getRowValue(index));


                    if(val.contains(request.getParameter(DataTableConstants.SEARCH_VALUE)))
                        flag = true;
                }
                catch (Exception e){

                }
            }

            if(flag){

                datas.add(source.getRowValue(index));
            }

        }

        return new CustomGridDataSource(datas);

    }

    /**
     * This method will set all the Sorting stuffs, thanks to DataTable parameters, coming from the request.
     * see https://datatables.net/manual/server-side for more details
     *
     * @param source the grid data source
     */
    public void prepareResponse(GridDataSource source){


    	String sord = request.getParameter(DataTableConstants.ORDER_DIR);
    	String sidx = request.getParameter(DataTableConstants.ORDER_IDX);

    	if(InternalUtils.isNonBlank(sidx))
        {
    		List<String> names = model.getPropertyNames();

            int indexProperty = Integer.parseInt(sidx);

            String propName = names.get(indexProperty);

            ColumnSort colSort =sortModel.getColumnSort(propName);

            if(!(InternalUtils.isNonBlank(colSort.name()) && colSort.name().startsWith(sord.toUpperCase())))
                    sortModel.updateSort(propName);
        }

    }

    /**
     * Method returning the desired data
     *
     * @param source the grid data source
     * @return the source???s data transformed to a JSONObject
     * @throws IOException in case something went wrong while rendering the response
     */
    public JSONObject getResponse(GridDataSource source) throws IOException {
        final String draw = request.getParameter(DataTableConstants.DRAW);
        final int records = source.getAvailableRows();

        if (records == 0){
            response.put(DataTableConstants.DRAW, draw);
            response.put(DataTableConstants.RECORDS_FILTERED, records);
            response.put(DataTableConstants.RECORDS_TOTAL, records);
            response.put(DataTableConstants.DATA, rows);
            return response;
        }

        int startIndex=0;
        String displayStart = request.getParameter(DataTableConstants.START);
        if(displayStart != null) startIndex=Integer.parseInt(displayStart);

        int rowsPerPage=records;
        String displayLength = request.getParameter(DataTableConstants.LENGTH);
        if(displayLength!=null) rowsPerPage=Integer.parseInt(displayLength);

        int endIndex= startIndex + rowsPerPage -1;
        if(endIndex>records-1) endIndex= records-1;

        source.prepare(startIndex,endIndex,sortModel.getSortConstraints() );

        /*
         * Add a filter to initialize the data to be sent to the client
         */
        pageRenderQueue.addPartialMarkupRendererFilter(
                new PartialMarkupRendererFilter() {

                    public void renderMarkup(MarkupWriter writer, JSONObject reply, PartialMarkupRenderer renderer)
                    {
                        reply.put(DataTableConstants.DATA, rows);
                        reply.put(DataTableConstants.DRAW, draw);
                        reply.put(DataTableConstants.RECORDS_TOTAL, records);
                        reply.put(DataTableConstants.RECORDS_FILTERED, records);

                        renderer.renderMarkup(writer, reply);
                    }
                }
        );

        for(int index=startIndex;index<=endIndex;index++)
        {
            JSONObject cell = new JSONObject();

            rows.put(cell);

            Object obj = source.getRowValue(index);

            List<String> names = model.getPropertyNames();
            int rowIndex = index%rowsPerPage;
            int columnIndex = 0;
            for (String name: names)
            {
                Block override = overrides.getOverrideBlock(name+"Cell");

                /*
                 * Is the property overridden as a block
                 */
                if (override != null){
                    /*
                     * Render the block from server-side !
                     */
                    addPartialMarkupRendererFilter(override,source.getRowType() , obj, name, rowIndex, columnIndex, index);
                }else{

                    PropertyConduit conduit = model.get(name).getConduit();

                    Object val = (conduit.get(obj) != null) ? conduit.get(obj) : "";

                    if (!String.class.equals(model.get(name).getClass())
                            && !Number.class.isAssignableFrom(model.get(name).getClass()))
                    {
                        Translator<Object> translator = translatorSource.findByType(model.get(name).getPropertyType());
                        if (translator != null)
                        {
                            val = translator.toClient(val);
                        }
                        else
                        {
                            val = val.toString();
                        }
                    }
                    /*
                     * Render the value from server-side !
                     */
                    addPartialMarkupRendererFilter(val,source.getRowType() , obj, name, rowIndex, columnIndex, index);
                }
                columnIndex++;
            }
        }


        /*
         * Even if it will be done once again in AjaxComponentEventRequestHandler , we must call partialRenderer.renderPartialPageMarkup() here to "flush" the PartialMarkupRendererFilters that we've added into the JSONArray !
         * It would be great if we could tell the partialRenderer that the job have already been done ...
         */
        partialRenderer.renderPartialPageMarkup();

        /*
         * Re-initialize the JSONArray for the next ajax request
         */
        rows = new JSONArray();

        return new JSONObject();
    }

    /*
     * This is the method we have to implement for the DataTableModel interface.
     * This is called in the DataTable component, when the data is loaded by ajax.
     *
     * (non-Javadoc)
     * @see org.got5.tapestry5.jquery.internal.DataTableModel#sendResponse(org.apache.tapestry5.http.services.Request, org.apache.tapestry5.grid.GridDataSource, org.apache.tapestry5.beaneditor.BeanModel, org.apache.tapestry5.grid.GridSortModel, org.apache.tapestry5.PropertyOverrides, boolean)
     */
    public JSONObject sendResponse(Request request, GridDataSource source, BeanModel model, GridSortModel sortModel, PropertyOverrides overrides, boolean mode) throws IOException {

        this.request = request;
        this.sortModel = sortModel;
        this.model = model;
        this.overrides = overrides;

        GridDataSource s = new CustomGridDataSource(source);

        /*
         * Filter available data in a normal mode.
         * For ajax mode, we give the opportunity to the developer to filter data on server-side
         */
        if(mode){
            if(InternalUtils.isNonBlank(request.getParameter(DataTableConstants.SEARCH_VALUE))) s = filterData(source);
        }

        prepareResponse(s);

        return getResponse(s);

    }

    /**
     * Add a PartialMarkupRendererFilter to process the rendering of a cell
     * Based on Zone rendering implementation
     * @param override, the block or the value to render
     * @param type, the type of the object to put inside the environment service. Required to render an item inside a loop
     * @param value, the value of the object to put inside the environment service. Required to render an item inside a loop
     * @param columnName the column???s name
     * @param rowIndex, the line number of the cell
     * @param columnIndex, the column number of the cell
     * @param globalIndex, the global index iteration
     */
    public void addPartialMarkupRendererFilter(final Object override, final Class type, final Object value, final String columnName, final int rowIndex, final int columnIndex, final int globalIndex) {

        final RenderCommand renderCommand = typeCoercer.coerce(override, RenderCommand.class);

        pageRenderQueue.addPartialMarkupRendererFilter(
                new PartialMarkupRendererFilter() {

                    public void renderMarkup(MarkupWriter writer, final JSONObject reply, PartialMarkupRenderer renderer)
                    {
                        RenderCommand forZone = new RenderCommand()
                        {
                            public void render(MarkupWriter writer, RenderQueue queue)
                            {
                                // Create an element to contain the content for the zone. We give it a mnemonic
                                // element name and attribute just to help with debugging (the element itself is discarded).

                                final Element zoneContainer = writer.element("ajax-partial");

                                ajaxFormUpdateController.setupBeforePartialZoneRender(writer);
                                /*
                                 * propagate the current item and the row index of the loop into the container
                                 * this is to allow the block to use it when rendering itself
                                 */
                                rowParam.set(value);
                                rowIndexParam.set(globalIndex);

                                queue.push(new RenderCommand()
                                {
                                    public void render(MarkupWriter writer, RenderQueue queue)
                                    {
                                        writer.end(); // the zoneContainer element

                                        // Need to do this Ajax Form-related cleanup here, before we extract the zone content.

                                        ajaxFormUpdateController.cleanupAfterPartialZoneRender();

                                        String zoneUpdateContent = zoneContainer.getChildMarkup();

                                        if(rows.length()<=rowIndex)
                                        {
                                            for(int i=0;i<rowIndex+1;i++)
                                                rows.put(new JSONObject() );
                                        }

                                        if(! (override instanceof Block)){

                                            /*
                                             * Must check JSONArray's length because partialRenderer.renderPartialPageMarkup() is done twice (see AjaxComponentEventRequestHandler)!
                                             */
                                            if(rows.length()>rowIndex)
                                            {
                                                rows.getJSONObject(rowIndex).put(columnName,override);
                                            }
                                        }else{
                                            /*
                                             * Must check JSONArray's length because partialRenderer.renderPartialPageMarkup() is done twice (see AjaxComponentEventRequestHandler)!
                                             */
                                            if(rows.length()>rowIndex)
                                            {
                                                rows.getJSONObject(rowIndex).put(columnName,zoneUpdateContent);
                                            }
                                        }
                                        zoneContainer.remove();
                                    }
                                });

                                // Make sure the zone's actual rendering command is processed first, then the inline
                                // RenderCommand just above.

                                queue.push(renderCommand);
                            }
                        };

                        pageRenderQueue.addPartialRenderer(forZone);

                        renderer.renderMarkup(writer, reply);
                    }
                }
        );
    }
}
