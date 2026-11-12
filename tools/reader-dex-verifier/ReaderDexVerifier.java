import java.lang.reflect.Method;

/** Runs under Android's ART, without installing or starting the application. */
public final class ReaderDexVerifier {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Expected reader class names");
        for (String name : args) {
            // Initialization forces ART verification, including methods that a JVM unit
            // test never sees after D8/R8 register allocation. Reader file facades only
            // initialize constants; no Activity or application data is involved.
            Class<?> type = Class.forName(name, true, ReaderDexVerifier.class.getClassLoader());
            Method[] methods = type.getDeclaredMethods();
            int largest = 0;
            String largestName = "";
            for (Method method : methods) {
                int words = java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? 0 : 1;
                for (Class<?> parameter : method.getParameterTypes()) {
                    words += parameter == long.class || parameter == double.class ? 2 : 1;
                }
                if (words > largest) {
                    largest = words;
                    largestName = method.getName();
                }
            }
            System.out.println("VERIFIED " + name + " methods=" + methods.length
                    + " largestArgumentWords=" + largest + " method=" + largestName);
        }
        System.out.println("READER_DEX_VERIFIED");
    }
}
