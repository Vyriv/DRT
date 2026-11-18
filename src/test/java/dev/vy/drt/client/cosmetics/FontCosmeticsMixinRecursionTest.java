package dev.vy.drt.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vy.drt.mixin.FontCosmeticsMixin;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FontCosmeticsMixinRecursionTest {
	@Test
	void nestedDecorationRestoresThreadLocalDepth() throws Exception {
		Method decorateSafely = FontCosmeticsMixin.class.getDeclaredMethod("drt$decorateSafely", Supplier.class);
		decorateSafely.setAccessible(true);
		Field depthField = FontCosmeticsMixin.class.getDeclaredField("drt$decorationDepth");
		depthField.setAccessible(true);
		@SuppressWarnings("unchecked")
		ThreadLocal<Integer> depth = (ThreadLocal<Integer>) depthField.get(null);

		Supplier<Integer> nested = () -> {
			assertEquals(1, depth.get());
			try {
				return (Integer) decorateSafely.invoke(null, (Supplier<Integer>) () -> {
					assertEquals(2, depth.get());
					return 42;
				});
			} catch (ReflectiveOperationException exception) {
				throw new AssertionError(exception);
			}
		};

		assertEquals(42, decorateSafely.invoke(null, nested));
		assertEquals(0, depth.get());
		depth.remove();
	}
}
