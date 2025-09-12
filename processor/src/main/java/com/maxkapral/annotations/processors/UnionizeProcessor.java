package com.maxkapral.annotations.processors;

import com.maxkapral.annotations.Unionize;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes({"com.maxkapral.annotations.Unionize"})
public final
class UnionizeProcessor extends AbstractProcessor {
    private static final TypeVariableName T = TypeVariableName.get("T");

    @Override
    public
    SourceVersion getSupportedSourceVersion () {
        return SourceVersion.latestSupported();
    }

    @Override
    public
    boolean process (Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Unionize.class)) {
            validateElement(element);
            var unionize = element.getAnnotation(Unionize.class);
            var union = new Union(getUnionizeTypes(unionize), Arrays.asList(unionize.names()));
            validateUnionize(union, element);
            try {
                generateSource(union, element);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return true;
    }

    private void generateSource(Union union, Element element) throws IOException {
        String packageName = this.processingEnv.getElementUtils().getPackageOf(element).toString();
        Function<String, ClassName> classNameFunc = (String name) -> ClassName.get(packageName, name);
        String interfaceName = element.getSimpleName() + "Union";
        ClassName interfaceType = classNameFunc.apply(interfaceName);

        List<TypeSpec> recordWrappers = new ArrayList<>();
        final TypeSpec iface;
        {
            var ifaceBuilder = TypeSpec.interfaceBuilder(interfaceName)
                                       .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
                                       .addMethod(MethodSpec.methodBuilder("value")
                                                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                                            .returns(Object.class)
                                                            .build());

            var setterBuilder = MethodSpec.methodBuilder("match")
                                          .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                                          .addTypeVariable(T);

            var getterBuilder = MethodSpec.methodBuilder("match")
                                          .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                                          .addTypeVariable(T)
                                          .addStatement("$T t", T)
                                          .returns(T);

            for (int i = 0; i < union.names().size(); ++i) {
                String name = union.names().get(i);
                TypeMirror clazz = union.types().get(i);
                TypeName clazzType = ClassName.get(clazz);

                ParameterSpec param = ParameterSpec.builder(clazzType, "value")
                                                   .build();
                MethodSpec ctor = MethodSpec.constructorBuilder()
                                            .addParameter(param)
                                            .build();
                TypeSpec record = TypeSpec.recordBuilder(name)
                                          .recordConstructor(ctor)
                                          .addSuperinterface(interfaceType)
                                          .build();
                recordWrappers.add(record);

                ClassName recordType = classNameFunc.apply(record.name());

                MethodSpec of = MethodSpec.methodBuilder("of")
                                          .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                          .addParameter(param)
                                          .addStatement("return new $T($N)", recordType, param.name())
                                          .returns(interfaceType)
                                          .build();
                ifaceBuilder.addMethod(of);

                TypeName biconsumer = ParameterizedTypeName.get(ClassName.get(BiConsumer.class), clazzType, T);
                MethodSpec setter = MethodSpec.methodBuilder(name + "Match")
                                              .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                                              .addTypeVariable(T)
                                              .addParameter(biconsumer, "biconsumer")
                                              .addParameter(T, "t")
                                              .beginControlFlow("if (this instanceof $T inst0)", recordType)
                                              .addStatement("biconsumer.accept(inst0.value(), t)")
                                              .endControlFlow()
                                              .build();
                ifaceBuilder.addMethod(setter);

                setterBuilder.addParameter(biconsumer, "biconsumer" + i)
                             .addStatement("$N($N, t)", setter.name(), "biconsumer" + i);

                TypeName function = ParameterizedTypeName.get(ClassName.get(Function.class), clazzType, T);
                MethodSpec getter = MethodSpec.methodBuilder(name + "Match")
                                              .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                                              .addTypeVariable(T)
                                              .addParameter(function, "function")
                                              .beginControlFlow("if (this instanceof $T inst0)", recordType)
                                              .addStatement("return function.apply(inst0.value())")
                                              .endControlFlow()
                                              .addStatement("return null")
                                              .returns(T)
                                              .build();
                ifaceBuilder.addMethod(getter);

                getterBuilder.addParameter(function, "function" + i)
                             .addStatement("t = $N($N)", getter.name(), "function" + i)
                             .beginControlFlow("if (t != null)")
                             .addStatement("return t")
                             .endControlFlow();
            }

            setterBuilder.addParameter(T, "t");
            ifaceBuilder.addMethod(setterBuilder.build());

            getterBuilder.addStatement("return null");
            ifaceBuilder.addMethod(getterBuilder.build());

            ifaceBuilder.addPermittedSubclasses(recordWrappers.stream()
                                                              .map(TypeSpec::name)
                                                              .map(classNameFunc)
                                                              .toList());
            iface = ifaceBuilder.build();
        }

        Filer filer = processingEnv.getFiler();

        JavaFile.builder(packageName, iface)
                .build()
                .writeTo(filer);

        for (var record : recordWrappers) {
            JavaFile.builder(packageName, record)
                    .build()
                    .writeTo(filer);
        }
    }

    private void validateElement(Element element) {
        TypeElement typeElement = (TypeElement) element;

        Set<Modifier> modifiers = typeElement.getModifiers();

        if (typeElement.getKind() != ElementKind.INTERFACE ||
            modifiers.contains(Modifier.PUBLIC) ||
            modifiers.contains(Modifier.PROTECTED) ||
            modifiers.contains(Modifier.PRIVATE))
        {
            this.error("@Unionize can only be applied to package-private interfaces",  typeElement);
        }

        if (!typeElement.getInterfaces().isEmpty()) {
            this.error("@Unionize interface cannot extend", typeElement);
        }

        if (!typeElement.getEnclosedElements().isEmpty()) {
            this.error("@Unionize interface body should be empty", typeElement);
        }
    }

    private void validateUnionize(Union union, Element element) {
        if (union.types().size() != union.names().size()) {
            this.error("@Unionize types and names must have the same length", element);
        }

        if (union.types().stream().distinct().count() != union.types().size()) {
            this.error("@Unionize types must be distinct", element);
        }

        if (union.names().stream().distinct().count() != union.names().size()) {
            this.error("@Unionize names must be distinct", element);
        }

        for (String name : union.names()) {
            if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
                this.error("@Unionize contains invalid name: " + name, element);
            }
        }

        for (TypeMirror type : union.types()) {
            if (type.getKind().isPrimitive()) {
                this.error("@Unionize contains primitive type: " + type, element);
            }
        }
    }

    private List<? extends TypeMirror> getUnionizeTypes(Unionize unionize) {
        try {
            Class<?>[] ignored = unionize.types();
            throw new UnsupportedOperationException();
        } catch (MirroredTypesException e) {
            return e.getTypeMirrors();
        }
    }

    private void error(String message, Element element) {
        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private record Union(List<? extends TypeMirror> types, List<String> names) {}
}
