package com.flyingangels.backend.importing;

import com.flyingangels.backend.athlete.dto.*;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Component
public class AthleteRegistrationParser {
    public record ParsedRow(int rowNumber,AthleteRequest athlete,String externalId,List<String> errors,List<String> warnings) {}
    public List<ParsedRow> parse(byte[] bytes,String filename) throws IOException {
        List<List<String>> rows=new ArrayList<>(); List<Integer> numbers=new ArrayList<>(); List<String> headers;
        if (filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            try (var book=WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                if (book.getNumberOfSheets()==0) throw new IllegalArgumentException("Workbook has no sheet");
                var sheet=book.getSheetAt(0); var first=sheet.getRow(sheet.getFirstRowNum());
                if (first==null) throw new IllegalArgumentException("Missing header row");
                headers=read(first,Set.of());
                Set<Integer> birthDateColumns=new HashSet<>();
                for(int c=0;c<headers.size();c++) {
                    String header=key(headers.get(c));
                    if(header.equals("dateofbirth") || header.equals("dob"))birthDateColumns.add(c);
                }
                for (int i=sheet.getFirstRowNum()+1;i<=sheet.getLastRowNum();i++) {
                    var row=sheet.getRow(i); if (row!=null) { rows.add(read(row,birthDateColumns));numbers.add(i+1); }
                    if (rows.size()>10000) throw new IllegalArgumentException("Maximum 10000 rows per import");
                }
            }
        } else {
            String text=new String(bytes,StandardCharsets.UTF_8).replaceFirst("^\uFEFF", "");
            // The club TSV labels this header with embedded quotes rather than CSV escaping.
            int headerEnd=text.indexOf('\n');
            if(headerEnd<0)headerEnd=text.length();
            text=text.substring(0,headerEnd).replace("\"FLYA\" Flying Angels Academy","FLYA Flying Angels Academy")+text.substring(headerEnd);
            var format=(text.split("\\R",2)[0].contains("\t")?CSVFormat.TDF:CSVFormat.DEFAULT).builder()
                .setHeader().setSkipHeaderRecord(true).setAllowMissingColumnNames(true).get();
            try (var parser=format.parse(new StringReader(text))) {
                headers=parser.getHeaderNames();
                for (var record:parser) {
                    List<String> row=new ArrayList<>();record.forEach(row::add);rows.add(row);numbers.add((int)record.getRecordNumber()+1);
                    if (rows.size()>10000) throw new IllegalArgumentException("Maximum 10000 rows per import");
                }
            }
        }
        Map<String,Integer> columns=new HashMap<>();
        for (int i=0;i<headers.size();i++) {
            String key=key(headers.get(i));
            if(key.isEmpty())continue;
            if (columns.put(key,i)!=null) throw new IllegalArgumentException("Duplicate header: "+headers.get(i));
        }
        int first=column(columns,"firstname"),last=column(columns,"lastname");
        if (first<0 || last<0) throw new IllegalArgumentException("FIRST NAME and LAST NAME headers are required");
        int dob=column(columns,"dateofbirth","dob"), gender=column(columns,"gender","sex"), note=column(columns,"note","notes");
        int fly=column(columns,"fly","flystatus","flyflyingangelstfacademy"),flya=column(columns,"flya","flyastatus","flyaflyingangelsacademy");
        int member=column(columns,"member","membernumber","memberid","registrationid");
        Set<Integer> metadata=new HashSet<>(Arrays.asList(first,last,dob,gender,note,fly,flya,member));
        List<ParsedRow> parsed=new ArrayList<>();
        for (int i=0;i<rows.size();i++) {
            var row=rows.get(i); if (row.stream().allMatch(String::isBlank)) continue;
            List<String> errors=new ArrayList<>(); List<String> warnings=new ArrayList<>();
            String fn=value(row,first),ln=value(row,last); if (fn.isBlank()) errors.add("First name is required");if(ln.isBlank())errors.add("Last name is required");
            if(fn.length()>100 || ln.length()>100) errors.add("Names must be 100 characters or fewer");
            if(row.size()>headers.size() && row.subList(headers.size(),row.size()).stream().anyMatch(v->!v.isBlank())) errors.add("More populated values than headers");
            LocalDate birth=null; String rawDate=value(row,dob);
            if(rawDate.isBlank()) errors.add("Birth date is required for import");
            else try {
                LocalDate supplied = LocalDate.parse(rawDate);
                if (supplied.isBefore(LocalDate.now())) birth = supplied;
                else errors.add("Birth date '" + rawDate + "' must be in the past");
            } catch(java.time.format.DateTimeParseException ex) {
                errors.add("Birth date '" + rawDate + "' is invalid; use YYYY-MM-DD or a typed XLSX date");
            }
            String gn=com.flyingangels.backend.athlete.Athlete.normalizeGender(value(row,gender)),nt=value(row,note),fs=com.flyingangels.backend.athlete.Athlete.normalizeStatus(value(row,fly)),fas=com.flyingangels.backend.athlete.Athlete.normalizeStatus(value(row,flya)),id=value(row,member);
            if(!gn.isBlank() && !gn.equals("Female") && !gn.equals("Male"))warnings.add("Unrecognized gender value: "+gn+"; retained for review");
            if(gn.isBlank())warnings.add("Gender is missing");
            if(gn.length()>30)errors.add("Gender must be 30 characters or fewer");
            if(nt.length()>500)errors.add("General note must be 500 characters or fewer");
            if(fs.length()>100 || fas.length()>100)errors.add("Team statuses must be 100 characters or fewer");
            if(id.length()>150)errors.add("External identifier must be 150 characters or fewer");
            List<AthleteEventRequest> events=new ArrayList<>();
            for(int c=0;c<headers.size();c++) if(!metadata.contains(c) && !value(row,c).isBlank()) {
                // Import only recognized performance columns; administrative columns remain in the original file.
                String event= headers.get(c).trim();
                if(com.flyingangels.backend.event.ResultValues.isPerformanceEvent(event)) {
                    String result=value(row,c);
                    var unit=com.flyingangels.backend.event.ResultValues.unitFor(event,com.flyingangels.backend.event.RankingOrder.ASC);
                    try {
                        com.flyingangels.backend.event.ResultValues.parse(result,unit,null);
                    } catch(IllegalArgumentException ex) { warnings.add("Review "+event+" value '"+result+"': expected "+unit+"; retained as unparsed historical data"); }
                    if(result.length()>50)errors.add("Result for "+event+" exceeds 50 characters");
                    else events.add(new AthleteEventRequest(event,result));
                }
            }
            parsed.add(new ParsedRow(numbers.get(i),new AthleteRequest(fn,ln,nt,fs,fas,birth,gn,events),id.isBlank()?null:id,List.copyOf(errors),List.copyOf(warnings)));
        }
        return parsed;
    }
    private static String key(String header){return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");}
    private static int column(Map<String,Integer> columns,String... aliases){for(String a:aliases)if(columns.containsKey(a))return columns.get(a);return -1;}
    private static String value(List<String> row,int index){return index<0 || index>=row.size()?"":row.get(index).trim();}
    private List<String> read(Row row,Set<Integer> birthDateColumns){
        List<String> values=new ArrayList<>();var formatter=new DataFormatter(Locale.ROOT);
        for(int i=0;i<row.getLastCellNum();i++){
            var cell=row.getCell(i,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if(cell==null)values.add("");
            // Excel time cells also count as date-formatted: only DOB columns become calendar dates.
            else if(birthDateColumns.contains(i) && cell.getCellType()==CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))values.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
            else values.add(formatter.formatCellValue(cell).trim());
        }return values;
    }
}
