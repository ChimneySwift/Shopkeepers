package com.nisovin.shopkeepers.config.value.types;

import java.lang.reflect.Type;
import java.util.List;

import com.nisovin.shopkeepers.config.value.TypePattern;
import com.nisovin.shopkeepers.config.value.TypePatterns;
import com.nisovin.shopkeepers.config.value.ValueType;
import com.nisovin.shopkeepers.config.value.ValueTypeProvider;
import com.nisovin.shopkeepers.config.value.ValueTypeProviders;

public class ColoredStringListValue extends ListValue<String> {

	public static final ColoredStringListValue INSTANCE = new ColoredStringListValue();
	public static final TypePattern TYPE_PATTERN = TypePatterns.parameterized(List.class, String.class);
	public static final ValueTypeProvider PROVIDER = ValueTypeProviders.forTypePattern(TYPE_PATTERN, type -> INSTANCE);

	public static final class Provider implements ValueTypeProvider {
		@Override
		public ValueType<?> get(Type type) {
			return PROVIDER.get(type);
		}
	}

	public ColoredStringListValue() {
		super(ColoredStringValue.INSTANCE);
	}
}
