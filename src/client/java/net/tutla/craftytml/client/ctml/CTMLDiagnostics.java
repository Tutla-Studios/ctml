package net.tutla.craftytml.client.ctml;

import net.tutla.tums.tusan.TusanContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the engine says something is wrong with a script without giving up on it.
 * The interpreter's own error() stops the parse where it stands, which is right for a value of the
 * wrong type and far too much for a misspelled class: one typo would take the whole screen with it.
 * So a warning is printed in the same shape as an error, with the same arrows under the place in
 * the file it came from, and the parse carries on.
 * Every note is kept as well, so a test or a tool can read back what a script was told.
 */
public final class CTMLDiagnostics {
    /** How many notes are kept before the oldest are dropped, so a bad loop cannot eat the heap. */
    private static final int KEPT = 200;

    private static final List<Note> NOTES = new ArrayList<>();
    private static boolean printing = true;

    /** One thing said about a script: what it was about, and where in the file it came from. */
    public record Note(String tag, String message, String where) {
        @Override
        public String toString(){
            return tag + ": " + message;
        }
    }

    private CTMLDiagnostics(){}

    /**
     * Says something is wrong and carries on. ctx is whatever was being interpreted at the time, and
     * only provides the place in the file; without one the note still stands, it just cannot point.
     */
    public static Note warn(String tag, String message, TusanContext ctx){
        Note note = new Note(tag, message, where(ctx));
        synchronized (NOTES) {
            NOTES.add(note);
            while (NOTES.size() > KEPT) NOTES.removeFirst();
        }
        if (printing) print(note);
        return note;
    }

    private static String where(TusanContext ctx){
        if (ctx == null || ctx.getInterpreter() == null) return null;
        try {
            return ctx.getInterpreter().arrowsAtPosition();
        } catch (Exception | LinkageError ignored) {
            return null; // nothing worth losing a warning over
        }
    }

    private static void print(Note note){
        StringBuilder sb = new StringBuilder("\n=============== WARNING ===============\n");
        sb.append(note.tag()).append(" : ").append(note.message()).append("\n");
        if (note.where() != null) {
            sb.append("============== POSITION ===============\n").append(note.where()).append("\n");
        }
        sb.append("=======================================");
        System.out.println(sb);
    }

    /** Everything said so far, oldest first. */
    public static List<Note> notes(){
        synchronized (NOTES) {
            return List.copyOf(NOTES);
        }
    }

    /** The notes whose tag matches, for whatever only cares about one kind. */
    public static List<Note> notes(String tag){
        List<Note> matching = new ArrayList<>();
        for (Note note : notes()) if (note.tag().equals(tag)) matching.add(note);
        return Collections.unmodifiableList(matching);
    }

    public static void clear(){
        synchronized (NOTES) {
            NOTES.clear();
        }
    }

    /** Turns the printing off, for a test that would rather read the notes than watch them go by. */
    public static void printing(boolean on){
        printing = on;
    }
}
