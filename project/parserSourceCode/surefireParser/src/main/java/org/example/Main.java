package org.example;

import java.io.IOException;
import java.io.File;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jgit.api.errors.GitAPIException;

public class Main {
    public static void main(String[] args) throws GitAPIException, IOException, InterruptedException {
        
        if (args.length == 0) {
            System.err.println("Call with xml path");
            System.exit(1);
        }

        SurefireParser.SurefireResult result;
        String filePath = args[0];
        try {
            File input = new File(filePath);
            if (input.isDirectory()) {
                result = SurefireParser.parseDirectory(filePath);
            } else {
                result = SurefireParser.parse(filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Pretty‑print with Gson
        Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

        String json = gson.toJson(result);
        System.out.println(json);

    }
}

