package de.scrum_master.stackoverflow.q64561436;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.jar.asm.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class JavassistMethodReturnValueReplacer implements ClassFileTransformer {
  public static boolean REPAIR_STACK_MAP_USING_ASM = false;
  public static boolean LOG_RETURN_VALUE_REPLACER = true;
  public static boolean DUMP_CLASS_FILES = true;
  public static String DUMP_CLASS_BASE_DIR = "transformed";
  private static final String LOG_PREFIX = "[Javassist Return Value Replacer] ";

  private final ClassPool classPool = ClassPool.getDefault();
  private final Set<String> targetClasses;

  public static void main(String[] args) throws UnmodifiableClassException {
    System.out.println(TargetClass.class.getCanonicalName());
    System.out.println(new TargetClass().greet("world"));
    Instrumentation instrumentation = ByteBuddyAgent.install();
    instrumentation.addTransformer(new JavassistMethodReturnValueReplacer(TargetClass.class), true);
    instrumentation.retransformClasses(TargetClass.class);
    System.out.println(new TargetClass().greet("world"));
  }

  public JavassistMethodReturnValueReplacer(Class<?>... targetClasses) {
    this.targetClasses = Arrays
      .stream(targetClasses)
      .map(Class::getName)
      .collect(Collectors.toSet());
  }

  @Override
  public byte[] transform(
    ClassLoader loader,
    String className,
    Class<?> classBeingRedefined,
    ProtectionDomain protectionDomain,
    byte[] classfileBuffer
  )
  {
    String canonicalClassName = className.replace('/', '.');
    if (!shouldTransform(canonicalClassName))
      return null;
    if (LOG_RETURN_VALUE_REPLACER)
      log("Starting transformation for class " + canonicalClassName);
    CtClass targetClass;
    try {
      // Caveat: Do not just use 'classPool.get(className)' because we would miss previous transformations.
      // It is necessary to really parse 'classfileBuffer'.
      targetClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
    }
    catch (Exception e) {
      log("ERROR: Cannot parse bytes for input class " + canonicalClassName);
      e.printStackTrace();
      return null;
    }

    try {
      applyTransformations(targetClass);
    }
    catch (Exception e) {
      log("ERROR: Cannot apply transformations to input class " + canonicalClassName);
      e.printStackTrace();
      return null;
    }

    byte[] transformedBytecode;
    try {
      transformedBytecode = targetClass.toBytecode();
    }
    catch (Exception e) {
      log("ERROR: Cannot get byte code for transformed class " + canonicalClassName);
      e.printStackTrace();
      return null;
    }

    if (REPAIR_STACK_MAP_USING_ASM)
      transformedBytecode = repairStackMapUsingASM(className, transformedBytecode);

    if (DUMP_CLASS_FILES) {
      Path path = new File(DUMP_CLASS_BASE_DIR + "/" + className + ".class").toPath();
      try {
        Files.createDirectories(path.getParent());
        log("Dumping transformed class file " + path.toAbsolutePath());
        Files.write(path, transformedBytecode);
      }
      catch (Exception e) {
        log("ERROR: Cannot write class file to " + path.toAbsolutePath());
        e.printStackTrace();
      }
    }

    return transformedBytecode;
  }

  public boolean shouldTransform(String className) {
    return targetClasses.contains(className);
  }

  public void applyTransformations(CtClass targetClass) {
    targetClass.defrost();
    try {
      replaceReturnValue(targetClass);
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot replace return values for class " + targetClass.getName(), e);
    }
    targetClass.detach();
  }

  private void replaceReturnValue(CtClass targetClass) throws CannotCompileException {
    // Fails in Javassist without ASM stack map repair
    String injectedCode = "return null;";
    // Works in Javassist without ASM stack map repair
    // String injectedCode = "if (true) return null;"
    for (CtMethod ctMethod : targetClass.getDeclaredMethods()) {
      if (LOG_RETURN_VALUE_REPLACER) {
        log("Replacing return value for method " + ctMethod.getLongName());
        log(injectedCode);
      }
      ctMethod.insertBefore(injectedCode);
    }
  }

  private byte[] repairStackMapUsingASM(String className, byte[] transformedBytecode) {
    if (DUMP_CLASS_FILES) {
      Path path = new File(DUMP_CLASS_BASE_DIR + "/" + className + ".unrepaired.class").toPath();
      try {
        Files.createDirectories(path.getParent());
        log("Dumping (unrepaired) transformed class file " + path.toAbsolutePath());
        Files.write(path, transformedBytecode);
      }
      catch (Exception e) {
        log("ERROR: Cannot write (unrepaired) class file to " + path.toAbsolutePath());
        e.printStackTrace();
      }
    }

    // Repair stack map frames via ASM
    ClassReader classReader = new ClassReader(transformedBytecode);

    // Directly passing the writer to the reader leads to re-ordering of the constant pool table. This is not a
    // problem with regard to functionality as such, but more difficult to diff when comparing the 'javap' output with
    // the corresponding result created directly via ASM.
    //
    //   ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    //   classReader.accept(classWriter, ClassReader.SKIP_FRAMES);
    //
    // So we use this slightly more complicated method which copies the original constant pool, new entries only being
    // appended to it as needed. Solution taken from https://stackoverflow.com/a/46644677/1082681.
    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES);
    classReader.accept(
      new ClassVisitor(Opcodes.ASM5, classWriter) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
          MethodVisitor writer = super.visitMethod(access, name, desc, signature, exceptions);
          return new MethodVisitor(Opcodes.ASM5, writer) {};
        }
      },
      ClassReader.SKIP_FRAMES
    );

    return classWriter.toByteArray();
  }

  private void log(String message) {
    System.out.println(LOG_PREFIX + message);
  }

}
