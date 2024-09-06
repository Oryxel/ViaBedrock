/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2024 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.api.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import team.unnamed.mocha.parser.MolangParser;
import team.unnamed.mocha.parser.ParseException;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.ExpressionInterpreter;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.NumberValue;
import team.unnamed.mocha.runtime.value.Value;

// MochaEngineImpl only return double, but sometimes it will still return string, so we have this.
public class MochaEngineUtil<T> {
    private final Scope scope;
    private final T entity;

    public MochaEngineUtil(T entity, Scope.Builder scopeBuilder) {
        this.scope = scopeBuilder.build();
        this.entity = entity;
    }

    public static MochaEngineUtil build() {
        Scope.Builder builder = Scope.builder();
        builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        MutableObjectBinding variableBinding = new MutableObjectBinding();
        MutableObjectBinding queryBinding = new MutableObjectBinding();
        builder.set("variable", variableBinding);
        builder.set("v", variableBinding);
        builder.set("query", queryBinding);
        builder.set("q", queryBinding);

        return new MochaEngineUtil(null, builder);
    }

    public String eval(@NotNull String source) {
        Objects.requireNonNull(source, "script");
        StringReader reader = new StringReader(source);

        String var3;
        try {
            var3 = this.eval(reader);
        } catch (Throwable var6) {
            try {
                reader.close();
            } catch (Throwable var5) {
                var6.addSuppressed(var5);
            }

            throw var6;
        }

        reader.close();
        return var3;
    }

    public String eval(@NotNull List<Expression> expressions) {
        Scope local = this.scope.copy();
        MutableObjectBinding temp = new MutableObjectBinding();
        local.set("temp", temp);
        local.set("t", temp);
        local.readOnly(true);
        ExpressionInterpreter<T> evaluator = new ExpressionInterpreter(this.entity, local);
        Value lastResult = NumberValue.zero();
        Iterator var5 = expressions.iterator();

        while(var5.hasNext()) {
            Expression expression = (Expression)var5.next();
            lastResult = expression.visit(evaluator);
            Value returnValue = evaluator.popReturnValue();
            if (returnValue != null) {
                lastResult = returnValue;
                break;
            }
        }

        return lastResult.getAsString();
    }

    public String eval(@NotNull Reader source) {
        List parsed;
        try {
            parsed = this.parse(source);
        } catch (ParseException var4) {
            ParseException e = var4;
            return "0";
        } catch (IOException var5) {
            IOException e = var5;
            throw new UncheckedIOException("Failed to read from given reader", e);
        }

        return this.eval(parsed);
    }

    public @NotNull List<Expression> parse(@NotNull Reader reader) throws IOException {
        return MolangParser.parser(reader).parseAll();
    }

}
