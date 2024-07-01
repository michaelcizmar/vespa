package ai.vespa.schemals.tree;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.*;

public class CSTUtils {

    private static Position getPositionFromOffset(Node node, int offset) {
        TokenSource token = node.getTokenSource();
        int line = token.getLineFromOffset(offset) - 1;
        int startOfLineOffset = token.getLineStartOffset(line + 1);
        int column = offset - startOfLineOffset;
        return new Position(line, column);
    }

    public static Range getNodeRange(Node node) {
        Position start = getPositionFromOffset(node, node.getBeginOffset());
        Position end = getPositionFromOffset(node, node.getEndOffset());
        return new Range(start, end);
    }

    public static void printTree(PrintStream logger, Node node) {
        printTree(logger, node, 0);
    }

    public static void printTree(PrintStream logger, Node node, Integer indent) {
        Range range = getNodeRange(node);
        logger.println(new String(new char[indent]).replace("\0", "\t") + node.getClass().getName()
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
        );

        for (Node child : node) {
            printTree(logger, child, indent + 1);
        }
    }

    public static void printTree(PrintStream logger, SchemaNode node) {
        printTree(logger, node, 0);
    }

    public static void printTree(PrintStream logger, SchemaNode node, Integer indent) {
        Range range = node.getRange();
        logger.println(new String(new char[indent]).replace("\0", "\t") + node.getClassLeafIdentifierString()
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
                );

        for (int i = 0; i < node.size(); i++) {
            printTree(logger, node.get(i), indent + 1);
        }
    }

    public static void printTreeUpToPosition(PrintStream logger, Node node, Position pos) {
        printTreeUpToPosition(logger, node, pos, 0);
    }

    public static void printTreeUpToPosition(PrintStream logger, SchemaNode node, Position pos) {
        printTreeUpToPosition(logger, node, pos, 0);
    }

    public static void printTreeUpToPosition(PrintStream logger, Node node, Position pos, Integer indent) {
        Range range = getNodeRange(node);

        if (!positionLT(pos, range.getStart())) {
            boolean dirty = node.isDirty();
            logger.println(new String(new char[indent]).replace("\0", "\t") + node.getClass().getName() + (dirty ? " [DIRTY]" : "")
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
                    );
        }

        for (Node child : node) {
            printTreeUpToPosition(logger, child, pos, indent + 1);
        }
    }

    public static void printTreeUpToPosition(PrintStream logger, SchemaNode node, Position pos, Integer indent) {

        Range range = node.getRange();
        if (!positionLT(pos, range.getStart())) {
            boolean dirty = node.isDirty();
            logger.println(new String(new char[indent]).replace("\0", "\t") + node.getClassLeafIdentifierString() + (dirty ? " [DIRTY]" : "")
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
                    );
        }


        for (int i = 0; i < node.size(); i++) {
            printTreeUpToPosition(logger, node.get(i), pos, indent + 1);
        }
    }

    public static Boolean positionLT(Position lhs, Position rhs) {
        return (
            lhs.getLine() < rhs.getLine() || (
                lhs.getLine() == rhs.getLine() && 
                lhs.getCharacter() < rhs.getCharacter()
            )
        );
    }

    public static Boolean positionInRange(Range range, Position pos) {
        return (
            positionLT(pos, range.getEnd()) && (
                positionLT(range.getStart(), pos) ||
                range.getStart().equals(pos)
            )
        );
    }
}
