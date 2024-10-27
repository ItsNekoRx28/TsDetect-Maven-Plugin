package com.github.nekaso.tsdetectmavenplugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "tsdetect", defaultPhase = LifecyclePhase.TEST)
public class TSDetectMavenPluginMojo extends AbstractMojo {
    private File outputDirectory;

    public TSDetectMavenPluginMojo() {
        try {
            outputDirectory = Files.createTempDirectory("tsdetect_input").toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory for tsdetect input", e);
        }
    }

    @Parameter(property = "projectName", required = true)
    private String projectName;

    @Parameter(property = "testPath", required = true)
    private String testPath;

    @Parameter(property = "srcPath", required = false)
    private String srcPath;

    @Parameter(property = "tsdetectPath", required = false)
    private String tsdetectPath;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Starting tsdetect-maven-plugin");
        getLog().info("Project Name: " + projectName);
        getLog().info("Test Path: " + testPath);
        getLog().info("Source Path: " + srcPath);
        getLog().info("TSDetect Path: " + tsdetectPath);

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        Path testRoot = Paths.get(testPath);          // Directorio de tests
        Path productionRoot = Paths.get(srcPath);     // Directorio de producción
        Path inputCsv = Paths.get(outputDirectory.getPath(), "input.csv");             // Archivo CSV que contendrá los tests y sus correspondientes archivos de producción

        try (BufferedWriter writer = Files.newBufferedWriter(inputCsv)) {
            // Buscar y procesar cada archivo de prueba
            try (Stream<Path> testFiles = Files.walk(testRoot)) {
                List<String> csvLines = testFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Test.java")) // Filtra archivos de prueba
                    .map(testFile -> {
                        Path productionFile = productionRoot.resolve(testRoot.relativize(testFile).toString().replace("Test.java", ".java"));
                        
                        // Verifica si el archivo de producción correspondiente existe
                        if (Files.exists(productionFile)) {
                            return String.format("%s,%s,%s", projectName, testFile.toAbsolutePath(), productionFile.toAbsolutePath());
                        } else {
                            return null; // No existe el archivo de producción correspondiente
                        }
                    })
                    .filter(Objects::nonNull) // Elimina entradas nulas (sin archivos de producción correspondientes)
                    .collect(Collectors.toList());

                // Escribir todas las líneas en el archivo CSV
                for (String line : csvLines) {
                    writer.write(line + "\n");
                }
                
                System.out.println("CSV generado con éxito en: " + inputCsv.toAbsolutePath());
            }

            new ProcessBuilder("java", "-jar", tsdetectPath, inputCsv.toAbsolutePath().toString()).start();

        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + inputCsv, e);
        }
    }
}
