package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class Main {
    private static final String imageUrlPrefix = "https://www.hernandosheriff.org/Jail/Applications/JailSearch/ShowThumb.aspx?BookNo=";
    private static final String detailUrlPrefix = "https://www.hernandosheriff.org/Jail/Applications/JailSearch/JailSearchDetails.aspx?BookNo=";

    public static void main(String[] args) throws Exception {

        String settings = Files.readString(Path.of("settings.json"));
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {
        };

        HashMap<String, Object> params = mapper.readValue(settings, typeRef);

        DateTimeFormatter format = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        LocalDate start = LocalDate.parse(params.get("bookingDateStart").toString(), format);
        LocalDate end = LocalDate.parse(params.get("bookingDateEnd").toString(), format);

        List<Map<String, String>> list = new ArrayList<>();
        while (start.isBefore(end) || start.isEqual(end)) {
            String date = start.format(format);
            System.out.println(date + "...");
            parseDoc(params, date, list);
            start = start.plusDays(1);
        }

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
        Files.writeString(Path.of("result", "result.json"), json);
    }

    static public void parseDoc(Map<String, Object> params, String date, List<Map<String, String>> list) throws Exception {

        Document docGet = Jsoup.connect("https://www.hernandosheriff.org/Jail/Applications/JailSearch/").get();
        String __VIEWSTATE = docGet.getElementById("__VIEWSTATE").attr("value");
        String __VIEWSTATEGENERATOR = docGet.getElementById("__VIEWSTATEGENERATOR").attr("value");
        String __EVENTVALIDATION = docGet.getElementById("__EVENTVALIDATION").attr("value");

        Document doc = Jsoup.connect("https://www.hernandosheriff.org/Jail/Applications/JailSearch/")
                .data("__VIEWSTATE", __VIEWSTATE)
                .data("__VIEWSTATEGENERATOR", __VIEWSTATEGENERATOR)
                .data("__EVENTVALIDATION", __EVENTVALIDATION)
                .data("ctl00$ContentPlaceHolder1$tbFirstName", params.get("firstname").toString())
                .data("ctl00$ContentPlaceHolder1$tbLastName", params.get("lastName").toString())
                .data("ctl00$ContentPlaceHolder1$tbBookingDateFrom", date)
                .data("ctl00$ContentPlaceHolder1$tbBookingDateTo", date)
                .data("ctl00$ContentPlaceHolder1$btnSearch", "Search...")
                .data("ctl00$ContentPlaceHolder1$cbShowReleased", "on")
                .post();


        Elements tableBody = doc.select("#aspnetForm > div.container > table:nth-child(4) > tbody");
        Elements rows = tableBody.select("tr");

        for (Element row : rows) {
            try {
                handleRow(row.children(), list, date, (Integer) (params.getOrDefault("delay", 1000)));
            } catch (Exception e) {
                System.err.println("could not parse row: " + row);
                e.printStackTrace();
            }
        }
    }

    static void handleRow(Elements tds, List<Map<String, String>> list, String date, int delay) throws Exception {

        Iterator<Element> iterator = tds.iterator();
        Map<String, String> map = new HashMap<>();

        iterator.next();

        Element nameBucket = iterator.next();
        String bookingNumber = nameBucket.childNodes().get(4).toString().trim();
        map.put("inmateName", nameBucket.childNodes().get(0).toString().trim());
        map.put("raceSexDOB", nameBucket.childNodes().get(2).toString().trim());
        map.put("bookingNumber", bookingNumber);

        Element bookingDate = iterator.next();
        map.put("bookingDate", bookingDate.text());


        Element offenses = iterator.next();
        map.put("offenses", offenses.text());

        //Element image = iterator.next();
        map.put("image-url", imageUrlPrefix + bookingNumber);

        Document document = Jsoup.connect(detailUrlPrefix + bookingNumber).get();

        String details = document.toString();
        map.put("details", details);

        Path directory = Path.of("result", date.replace("/", "-"));
        Files.createDirectories(directory);
        Files.writeString(Paths.get(directory.toString(), bookingNumber + ".html"), details);
        list.add(map);
        sleep(delay);
    }

    static void sleep(int n) {
        try {
            Thread.sleep(n);
        } catch (Exception e) {
        }
    }
}