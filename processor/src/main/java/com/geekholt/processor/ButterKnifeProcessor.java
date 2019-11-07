package com.geekholt.processor;

import com.geekholt.annotation.BindView;
import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.geekholt.annotation.BindView")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ButterKnifeProcessor extends AbstractProcessor {

    private Filer mFiler;
    private Messager mMessager;
    private Elements mElementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mFiler = processingEnvironment.getFiler();
        mMessager = processingEnvironment.getMessager();
        mElementUtils = processingEnvironment.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> bindViewElements = roundEnvironment.getElementsAnnotatedWith(BindView.class);
        for (Element element : bindViewElements) {
            //1.获取包名
            PackageElement packageElement = mElementUtils.getPackageOf(element);
            String packName = packageElement.getQualifiedName().toString();
            print(String.format("package = %s", packName));

            //2.注解所在的类的类名
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            String className = enclosingElement.getSimpleName().toString();
            print(String.format("enclosindClass = %s", enclosingElement));


            //因为BindView只作用于filed，所以这里可直接进行强转
            VariableElement bindViewElement = (VariableElement) element;
            //3.获取注解的成员变量名
            String fieldName = bindViewElement.getSimpleName().toString();

            //4.获取注解元数据
            BindView bindView = element.getAnnotation(BindView.class);
            int id = bindView.value();
            print(String.format("%s = %d", fieldName, id));

            //4.生成文件
            createFile(packName, className, fieldName, id);
            return true;
        }
        return false;
    }

    private void createFile(String packName, String className, String fieldName, int id) {
        try {
            String newClassName = className + "$BindAdapterImp";
            JavaFileObject jfo = mFiler.createSourceFile(packName + "." + newClassName, new Element[]{});
            Writer writer = jfo.openWriter();
            writer.write("package " + packName + ";");
            writer.write("\n\n");
            writer.write("import com.geekholt.butterknife.BindAdapter;");
            writer.write("\n\n\n");
            writer.write("public class " + newClassName + " implements BindAdapter<" + className + "> {");
            writer.write("\n\n");
            writer.write("public void bind(" + className + " target) {");
            writer.write("target." + fieldName + " = target.findViewById(" + id + ");");
            writer.write("\n");
            writer.write("  }");
            writer.write("\n\n");
            writer.write("}");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private void print(String msg) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, msg);
    }


}

