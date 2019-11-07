![](https://upload-images.jianshu.io/upload_images/10992781-52cab790297c0317.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

# 前言

目前Android社区涌现出越来越多的IOC框架，`ButterKnife`、`Dagger2`、`EventBus3`，这些框架往往能有效帮助我们简化代码，模块解耦，相信很多人也或多或少的用过其中一些框架。但是，有没有人想过这些框架的内部原理都是怎么样的呢？本文就从`ButterKnife`入手，手把手教你实现一个仿`ButterKnife`的IOC框架

# 知识准备

## Annotation

我们知道`annotation`有三个保留级别

- `RetentionPolicy.SOURCE` 注解只在源码阶段保留，在编译器进行编译时它将被丢弃忽视。
- `RetentionPolicy.CLASS` 注解只被保留到编译进行的时候，它并不会被加载到 JVM 中。
- `RetentionPolicy.RUNTIME` 注解可以保留到程序运行的时候，它会被加载进入到 JVM 中，所以在程序运行时可以获取到它们

`annotation`实际上就是一个标签，单独存在的时候没有任何实际意义。为了便于理解，这里再延伸一下另一个词语—Hook，Hook的英文解释是钩子。依我的理解，注解实际上就像这个钩子，勾住”类“、”方法“、”字段“，为了后续想对这些被“勾住”的东西做一些操作提供了方便

更多关于注解的知识可以自己查看相关资料，这里就不多做介绍了

## AnnotationProcessor

`annotationProcessor`是**APT**工具中的一种，他是Google开发的内置框架，不需要引入，可以直接在`build.gradle`文件中使用，如下:

```groovy
  dependencies {
    annotationProcessor project(':compiler') 
  }
```

![APT完成的工作](https://upload-images.jianshu.io/upload_images/10992781-257622562b5f3c6e?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

**APT**简单的说就是**注解处理器**，主要作用是可以编写一些规则在编译期间找出项目中的特定注解，以注解中的参数作为输入，生成文件.java文件作为输出。注意，**这里的重点是生成.java文件，而不能修改已经存在的Java类**，例如不能向已有的类中添加方法

# 开始手写”ButterKnife“

## ButterKnife使用简单介绍

先来看一下`ButterKnife`的常规使用方法，我们可以在`Activity`中的任意方法中直接使用这个`textView`，省去了`findViewById`的操作

```java
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.txt_test)
    TextView textView;
  	@BindView(R.id.btn_test)
  	Button button

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //调用框架方法
      	ButterKnife.bind(this);
      	//业务代码
      	textView.setText("Hello World");
    		button.setOnClickListenr(new OnClickListener(View view){})
    }
}
```

我们先不去看源码，我们可以设想一下`ButterKnife.bind(this)`做了什么事情，我认为大概是像下面这样：

```java
public class ButterKnife{
		public static void bind(MainActivity activity){
   			activity.textView = activity.findViewById(R.id.txt_test);
   			activity.button = activity.findViewById(R.id.btn_test)
		}
}
```

接下来会遇到几个问题：

**问题一：**我们如何将控件的引用和控件的id关联起来？我想我们应该很快有答案了，`@BindView`注解其实就是起到了关联的作用

**问题二：**前面说到，APT只能生成`.java`文件，而不能直接在方法中插入代码。所以我们能想到的是，我们可以通过APT生成`.java`文件，然后在运行时通过反射调用它，如下所示

1. 创建一个接口（接口是一种约束）,这里用到了泛型，因为我们要适用所有`Activity`

```java
public interface BindAdapter<T> {
    void bind(T activity);
}
```

2. 我们通过APT生成`BindAdapterImp`类，实现`BindAdapter`接口

```java
public class BindAdapterImp implement BindAdapter<MainActivity>{
		public void bind(MainActivity activity) {
				activity.textView = activity.findViewById(R.id.txt_test);
    		activity.button = activity.findViewById(R.id.btn_test)
  	}
}
```

3. 在`ButterKnife`的`bind()`里，通过反射生成`BindAdapterImp`，在调用其`bind()`

```java
public class ButterKnife {
   private static final String CLASS_NAME = "";
		public static void bind(Activity activity){
      	//反射拿到class
				Class<?> bindAdapterClass = Class.forName(CLASS_NAME);
      	//通过class拿到BindAdapterImp对象
        BindAdapterImp adapter = (BindAdapterImp) bindAdapterClass.newInstance();
      	//调用bind
        adapter.bind(activituy)
		}
}
```

**问题三：**问题又来了，我们把生成的`BindAdapterImp`类放到哪个包下面能让所有类都能调用到呢？答案是**内部类**！

意味着我们会为每一个调用了`ButterKnife.bind()`的`Activity`生成一个`BindAdapterImp`内部类，**内部类在编译期间生成的实际上是单独一个.java文件**，所以我们对上面的思路进行了一些优化，如下所示

```java
//这里的 MainActivity 是根据不同的Activity进行变化的
public class MainActivity$BindAdapterImp implement BindAdapter<MainActivity>{
		public void bind(MainActivity activity) {
				activity.textView = activity.findViewById(R.id.txt_test);
    		activity.button = activity.findViewById(R.id.btn_test)
  	}
}
```

```java
public class ButterKnife {
    private static final String SUFFIX = "$BindAdapterImp";
		//做了一个缓存，只有第一次bind时才通过反射创建对象
    static Map<Class, BindAdapter> mBindCache = new HashMap();

    public static void bind(Activity target){
        BindAdapter bindAdapter;
        if (mBindCache.get(target) != null) {
          	//如果缓存中有activity，从缓存中取
            bindAdapter = mBindCache.get(target);
        } else {
          	//缓存中没有，创建一个
            String adapterClassName = target.getClass().getName() + SUFFIX;
            Class<?> aClass = Class.forName(adapterClassName);
            bindAdapter = (BindAdapter) aClass.newInstance();
            mBindCache.put(aClass, bindAdapter);
        }
				//调用bind
        bindAdapter.bind(target);
    }   
}
```

> Tips：从上面的代码我们发现，为了尽量避免反射的性能消耗，`ButterKnife`内部会有一个缓存，这是一种典型的空间换时间的做法。在做内存优化的时候，我们往往会提到尽量少用`ButterKnife`这种依赖注入框架其实就是这个原因。这个还需要大家对各自项目作出一个折中的选择

最后，我们面临的问题实际上就是如何在编译期生成上面`BindAdapterImp`类，接下来跟着我一步步来吧

# 创建一个项目

![](https://upload-images.jianshu.io/upload_images/10992781-14a9da9fa858f89f.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

**注意这里不要勾选androidx，不然注解处理器会失效。想要支持androidx，需要使用Kotlin，然后用kapt取代AnnotationProcessor**

## 创建一个注解类

新建一个**java module**，命名为**annotation**

创建编译器注解类`@BindView`，这是一个属性注解，只有在编译期有效，经过编译后，注解信息会被丢弃，不会保留到编译好的`class`文件里

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface BindView {
    int value();
}
```

## 创建AnnotationProcessor

新建一个**java module**，命名为**processor**

创建注解处理器，在编译期间去扫描`@BindView`所标注的属性

```java
@AutoService(Processor.class)
@SupportedAnnotationTypes("com.geekholt.annotation.BindView")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class GeekKnifeProcessor extends AbstractProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        return false;
    }
}
```

- **@AutoService(Processor.class)**：向`javac`注册我们这个自定义的注解处理器，这样，在`javac`编译时，才会调用到我们这个自定义的注解处理器方法
- **@SupportedAnnotationTypes()**：表示我们这个注解处理器所要处理的注解
- **@SupportedSourceVersion()**：代表JDK版本号，这里是代表java8
- **init()**：初始化时会自动被调用，并传入`processingEnvironment`参数，通过该参数可以获取到很多有用的工具类: `Elements` **,** `Types` **,** `Filer` 等等
- **process()**：`AnnotationProcessor`扫描出的结果会存储进`roundEnvironment`中，可以从中获取注解所标注的内容信息
- **ProcessingEnvironment**

```java
/**用于提供工具类**/
public interface ProcessingEnvironment {
		//返回注解处理器的配置参数
    Map<String, String> getOptions();
  
		//Message用来报告错误，警告和其他提示信息
    Messager getMessager();
  
		//Filer用于创建新的源文件，class文件或辅助文件（可以用JavaPoet简化创建文件操作）
    Filer getFiler();
  
		//Elements包含用于操作Element的工具方法
    Elements getElementUtils();
  
		//Types包含用于操作TypeMirror的工具方法
    Types getTypeUtils();
  
		//返回Java版本
    SourceVersion getSourceVersion();
  
		//返回当前语言环境或者null（没有语言环境）
    Locale getLocale();
}
```

- **RoundEnvironment**

```java
/**用于获取注解所标注的内容信息**/
public interface RoundEnvironment {
    boolean processingOver();
		
  	//返回上一轮注解处理器是否产生错误
    boolean errorRaised();

  	//返回上一轮注解处理器生成的根元素
    Set<? extends Element> getRootElements();

  	//返回包含指定注解类型的元素的集合
    Set<? extends Element> getElementsAnnotatedWith(TypeElement var1);
		
  	//返回包含指定注解类型的元素的集合
    Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> var1);
}
```

- **Element**

`Element`代表一个静态的，语言级别的构件，对于Java源文件来说，`Element`代表程序元素：包，类，方法都是一种程序元素

`VariableElement`：代表一个字段，枚举常量，方法或者构造方法的参数，局部变量及异常参数等元素

`PackageElement`：代表包元素

`TypeElement`：代表类或接口元素

`ExecutableExement`：代表方法，构造函数，类或接口的初始化代码块等元素，也包括注解类型元素

- **TypeMirror**

`TypeMirror`代表java语言中的类型。`Types`包括基本类型、声明类型（类类型和接口类型）、数组、类型变量和空类型。 也代表通配类型参数，可执行文件的签名和返回类型等。`TypeMirror`类中最重要的是`getKind()`方法， 该方法返回`TypeKind`类型

> 简单来说，`Element`代表源代码，`TypeElement`代表的是源码中的类型元素，比如类。虽然我们可以从`TypeElement`中获取类名， 但是`TypeElement`中不包含类本身的信息，比如它的父类，要想获取这信息需要借助`TypeMirror`，可以通过`Element`中的`asType()` 获取元素对应的`TypeMirror`

## 创建BindAdapter接口

新建一个**android module**，命名为**butterknife**

创建`BindAdapter`接口

```java
package com.geekholt.butterknife;

public interface BindAdapter<T> {
    void bind(T activity);
}
```

# 处理依赖关系

- **app module**

```groovy
compileOnly project(':annotation')
annotationProcessor project(':processor')
api project(':butterknife')
```

- **processor module**

```groovy
api project(':annotation')
```

## 编写AnnotationProcessor

基本工作都已经做好了，我们的目标也已经很明确了，我们最终想要生成的就是像下面这样一个文件

```java
package com.geekholt.geekknife_example;

import com.geekholt.geekknife.adapter.BindAdapter;

public class MainActivity$BindAdapterImp implement BindAdapter<MainActivity>{
		public void bind(MainActivity activity) {
				activity.textView = activity.findViewById(R.id.txt_test);
    		activity.button = activity.findViewById(R.id.btn_test)
  	}
}
```

我们需要获取哪些内容呢？

- **包名**

- **注解所在的类的类名（Activity名）**

- **注解的成员变量名（控件名）**
- **注解的元数据（资源Id）**

所以，最终完成后的`AnnotationProcessor`就是下面这样，获取到我们需要的内容后，生成java文件，逻辑其实非常简单，只是相关的API不是很常用，可能需要熟悉一下

```java
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

  
  	/**创建文件**/
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

		/**打印编译期间的日志**/
    private void print(String msg) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, msg);
    }


}
```

rebuild一下项目，在相关目录下就可以看到我们想要的文件就已经成功生成了

![](https://upload-images.jianshu.io/upload_images/10992781-ec16a7b30e20daf2.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

## 运行时调用生成的代码

接下来的内容其实我们一开始就已经说过了，我们需要在运行时通过反射调用我们编译期生成的类

在**butterKnife module**下创建`ButterKnife`类

```java
public class ButterKnife {
    private static final String SUFFIX = "$BindAdapterImp";
    //做了一个缓存，只有第一次bind时才通过反射创建对象
    static Map<Class, BindAdapter> mBindCache = new HashMap();

    public static void bind(Activity target) {
        BindAdapter bindAdapter = null;
        if (mBindCache.get(target) != null) {
            //如果缓存中有activity，从缓存中取
            bindAdapter = mBindCache.get(target);
        } else {
            //缓存中没有，创建一个
            try {
                String adapterClassName = target.getClass().getName() + SUFFIX;
                Class<?> aClass = Class.forName(adapterClassName);
                bindAdapter = (BindAdapter) aClass.newInstance();
                mBindCache.put(aClass, bindAdapter);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        //调用bind
        if (bindAdapter != null) {
            bindAdapter.bind(target);
        }
    }
}
```

在我们的`MainActivity`中调用`ButterKnife.bind(this)`

```java
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.txt_main)
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        textView.setText("Hello ButterKnife");
    }
}
```

运行一下项目，看，“ButterKnife”就顺利工作了！是不是比想象的简单呢！

![运行结果](https://upload-images.jianshu.io/upload_images/10992781-7a1af402d7982d49.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

# 项目完整地址

https://github.com/Geekholt/ButterKnife


