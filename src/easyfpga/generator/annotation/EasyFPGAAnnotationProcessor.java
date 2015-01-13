/*
 *  This file is part of easyFPGA.
 *  Copyright 2013-2015 os-cillation GmbH
 *
 *  easyFPGA is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  easyFPGA is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with easyFPGA.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package easyfpga.generator.annotation;

import java.io.OutputStream;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;


/**
 * The annotation processor that finds the class that is annotated with @EasyFPGA and creates the
 * source of the BinaryBuilder from a template
 */
@SupportedAnnotationTypes("easyfpga.generator.annotation.EasyFPGA")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class EasyFPGAAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {

            /* get all EasyFPGA annotated elements */
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(EasyFPGA.class);
            if (annotatedElements.size() == 0) {
                return false;
            }
            if (annotatedElements.size() > 1) {
                for (Element elem : annotatedElements) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            "Too many EasyFPGA Modules defined, just one allowed.", elem);
                }
                return true;
            }

            /*
             * create a main application for every element in list. At this
             * point there should only be one to avoid errors.
             */
            for (Element elem : annotatedElements) {
                TypeMirror typeMirror = elem.asType();
                String typeQualifiedName = typeMirror.toString();
                try {
                    String application = createBinaryBuilderSource(typeQualifiedName);
                    /* The file to be written */
                    JavaFileObject file = processingEnv.getFiler().createSourceFile("easyfpga.generator.BinaryBuilder");
                    OutputStream outputStream = file.openOutputStream();
                    outputStream.write(application.getBytes());
                    outputStream.close();

                    processingEnv.getMessager().printMessage(Kind.NOTE,
                            String.format("BinaryBuilder for \"%s\" created.", typeQualifiedName));
                }
                catch (Exception e) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            e.getLocalizedMessage(), elem);
                }
            }
        }
        return true; // no further processing of this annotation type
    }

    /**
     * Create the BinaryBuilder.java from the template
     *
     * @param typeQualifiedName
     * @return a string representation of the file contents
     */
    private String createBinaryBuilderSource(String typeQualifiedName) {
        StringBuffer buffer = new StringBuffer();
        Scanner scanner = new Scanner(getClass().getResourceAsStream("/templates/builder/BinaryBuilder.template"));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains("%myfpga")) {
                line = line.replaceAll("%myfpga", typeQualifiedName);
            }
            buffer.append(String.format("%s\n", line));
        }
        scanner.close();
        return buffer.toString();
    }

}
