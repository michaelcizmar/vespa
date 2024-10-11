package ai.vespa.schemals.lsp.common.command.commandtypes;

import java.util.List;

import com.google.gson.JsonPrimitive;

import ai.vespa.schemals.context.EventExecuteCommandContext;
import ai.vespa.schemals.schemadocument.DocumentManager;

/**
 * DocumentParse
 */
public class DocumentParse implements SchemaCommand {
    private String fileURI;

	@Override
	public int getArity() {
        return 1;
	}

	@Override
	public boolean setArguments(List<Object> arguments) {
        assert arguments.size() == getArity();

        if (!(arguments.get(0) instanceof JsonPrimitive))
            return false;

        JsonPrimitive arg = (JsonPrimitive) arguments.get(0);
        this.fileURI = arg.getAsString();
        return true;
	}

	@Override
	public Object execute(EventExecuteCommandContext context) {
        DocumentManager document = context.scheduler.getDocument(fileURI);
        if (document == null) return null;
        document.reparseContent();
        return null;
	}
}
