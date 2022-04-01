package com.alibaba.datax.core.transport.transformer;

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.transformer.Transformer;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Arrays;

public class JavaScriptTransformer extends Transformer {
	public JavaScriptTransformer() {
		setTransformerName("js");
	}

	@Override
	public Record evaluate(Record record, Object... paras) {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		engine.put("record", record);

		String code = "print(\"run js\")";
		try {
			engine.eval(code);
		} catch (ScriptException e) {
			throw DataXException.asDataXException(TransformerErrorCode.TRANSFORMER_RUN_EXCEPTION, "paras:" + Arrays.asList(paras).toString() + " => " + e.getMessage());
		}

		// record.addColumn();

		return record;
	}
}