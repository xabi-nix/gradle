# Direct invocation experiment

This experiment provides a direct invoker for use in place of reflection. The idea is that reflection is slow,
so instead of invoking a method typically using `method.invoke(target, args)`, we will instead create a class,
at runtime, which will perform a direct invocation:

    public class Owner {
       int calc(int x, String param)
    }
    interface FastInvoker {
        Object invoke(Object target, Object... args)
    }

    // for a Method representing
    // generates something like this:
    public class GeneratedInvoker implements FastInvoker {
        Object void invoke(Object target, Object... args) {
           return Integer.valueOf(((Owner)target).calc((int) args[0], (String) args[1]));
        }
    }


A benchmark for this can be found in `org.gradle.experiments.reflection.DirectInvokerBenchmark`. The 2 categories of tests are:

  - `boxedArgument` : the target method doesn't return a primitive type,  nor have any primitive parameter type
  - `primitiveArguments` : the target method returns a primitive type and has all arguments of primitive type

 We separate the cases because for both reflection and direct invocation, we receive an array of `Object`, which means we will pay the cost of unboxing to primitive.

Here are the results:

    Benchmark                                                               Mode  Cnt          Score         Error  Units
    DirectInvokerBenchmark.boxedArgumentsBaseline                          thrpt   80   18192916.428 ±  728441.132  ops/s
    DirectInvokerBenchmark.boxedArgumentsGeneratedInvoker                  thrpt   80   17897064.539 ±  588633.873  ops/s
    DirectInvokerBenchmark.boxedArgumentsGeneratedInvokerThroughCache      thrpt   80    7648534.666 ±  276741.843  ops/s
    DirectInvokerBenchmark.boxedArgumentsReflection                        thrpt   80   14641337.897 ±  519423.273  ops/s
    DirectInvokerBenchmark.primitiveArgumentsBaseline                      thrpt   80  105768020.070 ± 2544460.731  ops/s
    DirectInvokerBenchmark.primitiveArgumentsGeneratedInvoker              thrpt   80   34583380.075 ± 3030439.059  ops/s
    DirectInvokerBenchmark.primitiveArgumentsGeneratedInvokerThroughCache  thrpt   80    9275721.323 ±  538279.606  ops/s
    DirectInvokerBenchmark.primitiveArgumentsReflection                    thrpt   80   33329061.026 ± 1973876.127  ops/s

The baseline is a direct, non reflective, method call. We can see that the generated invoker performance is, as expected, better than reflection. However, we need to generate the class, and this alone has an important cost. So to avoid paying this cost when invoking, we need to cache the generated class.

The results above show that the results with a thread-safe cache are significantly worse than with reflection. In conclusion, while a generated class proves to have better performance than reflection, the mandatory use of a cache kills the advantage of this solution.
