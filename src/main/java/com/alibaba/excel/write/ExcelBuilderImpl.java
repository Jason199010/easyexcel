package com.alibaba.excel.write;

import com.alibaba.excel.context.WriteContext;
import com.alibaba.excel.event.WriteHandler;
import com.alibaba.excel.exception.ExcelGenerateException;
import com.alibaba.excel.metadata.BaseRowModel;
import com.alibaba.excel.metadata.ExcelColumnProperty;
import com.alibaba.excel.metadata.Sheet;
import com.alibaba.excel.metadata.Table;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.util.CollectionUtils;
import com.alibaba.excel.util.POITempFile;
import com.alibaba.excel.util.TypeUtil;
import com.alibaba.excel.util.WorkBookUtil;
import net.sf.cglib.beans.BeanMap;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationConstraint;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTDataValidation;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.impl.CTDataValidationImpl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author jipengfei
 */
public class ExcelBuilderImpl implements ExcelBuilder {

    private WriteContext context;

    public ExcelBuilderImpl(InputStream templateInputStream,
                            OutputStream out,
                            ExcelTypeEnum excelType,
                            boolean needHead, WriteHandler writeHandler) {
        try {
            //初始化时候创建临时缓存目录，用于规避POI在并发写bug
            POITempFile.createPOIFilesDirectory();
            context = new WriteContext(templateInputStream, out, excelType, needHead, writeHandler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addContent(List data, int startRow) {
        if (CollectionUtils.isEmpty(data)) {
            return;
        }
        int rowNum = context.getCurrentSheet().getLastRowNum();
        if (rowNum == 0) {
            Row row = context.getCurrentSheet().getRow(0);
            if (row == null) {
                if (context.getExcelHeadProperty() == null || !context.needHead()) {
                    rowNum = -1;
                }
            }
        }
        if (rowNum < startRow) {
            rowNum = startRow;
        }
        for (int i = 0; i < data.size(); i++) {
            int n = i + rowNum + 1;
            addOneRowOfDataToExcel(data.get(i), n);
        }
    }

    @Override
    public void addContent(List data, Sheet sheetParam) {
        context.currentSheet(sheetParam);
        addContent(data, sheetParam.getStartRow());
    }

    @Override
    public void addContent(List data, Sheet sheetParam, Table table) {
        context.currentSheet(sheetParam);
        context.currentTable(table);
        addContent(data, sheetParam.getStartRow());
    }

    @Override
    public void merge(int firstRow, int lastRow, int firstCol, int lastCol) {
        CellRangeAddress cra = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
        context.getCurrentSheet().addMergedRegion(cra);
    }

    @Override
    public void finish() {
        try {
            context.getWorkbook().write(context.getOutputStream());
            context.getWorkbook().close();
        } catch (IOException e) {
            throw new ExcelGenerateException("IO error", e);
        }
    }

    /**
     * @return
     */
    @Override
    public WriteContext getContext() {
        return context;
    }

    /**
     * write constrain valation to sheet
     */
    public void addConstrainValationToSheet(Sheet sheetParam, String hiddenName,
                                            List<String> dataList1, int sheetIndex, int beginRow, int endRow, int beginColumn, int endColumn) {
        context.currentSheet(sheetParam);
        org.apache.poi.ss.usermodel.Sheet currentSheet = context.getCurrentSheet();
        DataValidationHelper helper = currentSheet.getDataValidationHelper();
        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) helper
                .createExplicitListConstraint(dataList1.toArray(new String[dataList1.size()]));
        // 设置区域边界
        CellRangeAddressList addressList = new CellRangeAddressList(beginRow, endRow, beginColumn, endColumn);
        XSSFDataValidation validation = (XSSFDataValidation) helper
                .createValidation(dvConstraint, addressList);
        // 输入非法数据时，弹窗警告框
        validation.setShowErrorBox(true);
        // 设置提示框
        //validation.createPromptBox("温馨提示", "请选择性别！！!");
        //validation.setShowPromptBox(true);
        currentSheet.addValidationData(validation);
    }

    private void addBasicTypeToExcel(List<Object> oneRowData, Row row) {
        if (CollectionUtils.isEmpty(oneRowData)) {
            return;
        }
        for (int i = 0; i < oneRowData.size(); i++) {
            Object cellValue = oneRowData.get(i);
            Cell cell = WorkBookUtil.createCell(row, i, context.getCurrentContentStyle(), cellValue,
                    TypeUtil.isNum(cellValue));
            if (null != context.getAfterWriteHandler()) {
                context.getAfterWriteHandler().cell(i, cell);
            }
        }
    }

    private void addJavaObjectToExcel(Object oneRowData, Row row) {
        int i = 0;
        BeanMap beanMap = BeanMap.create(oneRowData);
        for (ExcelColumnProperty excelHeadProperty : context.getExcelHeadProperty().getColumnPropertyList()) {
            BaseRowModel baseRowModel = (BaseRowModel) oneRowData;
            String cellValue = TypeUtil.getFieldStringValue(beanMap, excelHeadProperty.getField().getName(),
                    excelHeadProperty.getFormat());
            CellStyle cellStyle = baseRowModel.getStyle(i) != null ? baseRowModel.getStyle(i)
                    : context.getCurrentContentStyle();
            Cell cell = WorkBookUtil.createCell(row, i, cellStyle, cellValue,
                    TypeUtil.isNum(excelHeadProperty.getField()));
            if (null != context.getAfterWriteHandler()) {
                context.getAfterWriteHandler().cell(i, cell);
            }
            i++;
        }

    }

    private void addOneRowOfDataToExcel(Object oneRowData, int n) {
        Row row = WorkBookUtil.createRow(context.getCurrentSheet(), n);
        if (null != context.getAfterWriteHandler()) {
            context.getAfterWriteHandler().row(n, row);
        }
        if (oneRowData instanceof List) {
            addBasicTypeToExcel((List) oneRowData, row);
        } else {
            addJavaObjectToExcel(oneRowData, row);
        }
    }
}
