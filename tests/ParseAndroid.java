import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.util.*;
/** Verificação sintática sem exigir android.jar (não é compilação Android). */
public final class ParseAndroid {
  public static void main(String[] args)throws Exception {
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
    StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null);
    Iterable<? extends JavaFileObject> files=manager.getJavaFileObjectsFromStrings(Arrays.asList(args));
    JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,files);
    task.parse();
    int errors=0;
    for(Diagnostic<? extends JavaFileObject> d:diagnostics.getDiagnostics())
      if(d.getKind()==Diagnostic.Kind.ERROR){errors++;System.err.println(d);}
    manager.close();
    if(errors>0)throw new IllegalStateException(errors+" erros de sintaxe");
    System.out.println("OK: sintaxe da interface Android em Java.");
  }
}
