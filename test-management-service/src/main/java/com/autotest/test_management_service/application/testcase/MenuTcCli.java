package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Offline reproducible sample generation using the exact production generator/exporter. */
public class MenuTcCli {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: MenuTcCli <menu.xlsx> <output-directory>");
        Path input=Path.of(args[0]),directory=Path.of(args[1]);Files.createDirectories(directory);
        byte[] bytes=Files.readAllBytes(input);
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var mapper=new ObjectMapper();var reader=new MenuTcWorkbook(mapper);var generator=new MenuTcGenerator(mapper);
        var report=mapper.createObjectNode().put("sourceFile",input.getFileName().toString()).put("sourceSha256",digest).put("templateVersion",MenuTcGenerator.VERSION);
        var products=report.putArray("products");int index=0;
        for(var sheet:reader.read(new java.io.ByteArrayInputStream(bytes),digest)) {
            var output=generator.generate(sheet.path("productName").asText(),sheet.path("features"),sheet.path("source"));
            String prefix="product-"+(++index);
            Files.write(directory.resolve(prefix+".xlsx"),reader.export(output));
            Files.writeString(directory.resolve(prefix+".json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
            var product=products.addObject().put("sheet",sheet.path("source").path("sheet").asText()).put("features",output.path("featureCount").asInt()).put("testCases",output.path("caseCount").asInt())
                .put("workbook",prefix+".xlsx").put("json",prefix+".json");
            product.set("classificationCounts",output.path("summary"));product.set("skippedRows",sheet.path("skippedRows"));
            product.put("workbookSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(prefix+".xlsx")))));
            product.put("jsonSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(directory.resolve(prefix+".json")))));
        }
        Files.writeString(directory.resolve("generation-report.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("Generated "+products.size()+" independent product TC sets in "+directory);
    }
}
