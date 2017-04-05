/*
 * Copyright (c) 2017 Dragan Zuvic
 *
 * This file is part of jtsgen.
 *
 * jtsgen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jtsgen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jtsgen.  If not, see http://www.gnu.org/licenses/
 *
 */

package dz.jtsgen.processor.renderer.module;

import dz.jtsgen.processor.renderer.helper.ModuleResourceHelper;
import dz.jtsgen.processor.renderer.model.TypeScriptRenderModel;
import dz.jtsgen.processor.renderer.module.tsd.TSDGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
*  exports a  module
*  
 * Created by zuvic on 16.02.17.
 */
public class ModuleGenerator {

    private static Logger LOG = Logger.getLogger(ModuleGenerator.class.getName());

    private TypeScriptRenderModel model;
    private ProcessingEnvironment env;
    private final TSDGenerator tsdGenerator;

    public ModuleGenerator(TypeScriptRenderModel model, ProcessingEnvironment env) {
        this.model = model;
        this.env = env;
        this.tsdGenerator = new TSDGenerator(model, env);
    }

    public void writeModule(TSModule module) {
        try {
            writePackageJson(module);
            tsdGenerator.writeTypes(module);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Caught Exception", e);
            this.env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not write output file(s) " + e.getMessage());
        }
    }



    private void writePackageJson(TSModule module) throws IOException {
        String packageJson = PackageJsonGenerator.packageJsonFor(module, model);
        FileObject package_json_file_object = ModuleResourceHelper.createResource(env, module,"package.json");

        try (PrintWriter out = new PrintWriter(package_json_file_object.openWriter())) {
                out.println(packageJson);
        }
    }

}