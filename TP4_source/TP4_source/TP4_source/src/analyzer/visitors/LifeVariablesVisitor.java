package analyzer.visitors;

import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;


/**
 * Created: 19-02-15
 * Last Changed: 19-10-20
 * Author: Félix Brunet & Doriane Olewicki
 * Modified by: Gérard Akkerhuis
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class LifeVariablesVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private /*final*/ PrintWriter m_writer;

    public LifeVariablesVisitor(PrintWriter writer) { m_writer = writer; }

    /* UTIL POUR VARIABLES VIVES */
    public HashMap<String, StepStatus> allSteps = new HashMap<>();
    private HashSet<String> previous_step = new HashSet<>(); // dernier step qui est rencontré... sera la liste du/des STOP_NODE après parcours de tout l'arbre.

    /*Afin de pouvoir garder en memoire les variables a ajouter au REF*/
    private HashSet<String> current_ref_ids = new HashSet<>();

    private int step = 0;
    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genStep() { return "_step" + step++; }

    @Override
    public Object visit(SimpleNode node, Object data) { return data; }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        /*node.childrenAccept(this, data);
        compute_IN_OUT();

        // Impression déjà implémentée ici, vous pouvez changer cela si vous n'utilisez pas allSteps.
        for (int i = 0; i < step; i++) {
            m_writer.write("===== STEP " + i + " ===== \n" + allSteps.get("_step" + i).toString());
        }
        return null;*/

        HashSet<String> steps = (HashSet<String>) node.jjtGetChild(node.jjtGetNumChildren() - 1).jjtAccept(this, new HashSet<String>());
        previous_step = steps;
        compute_IN_OUT();

        for (int i = 0; i < node.jjtGetNumChildren() - 1; i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }

        // Impression déjà implémentée ici, vous pouvez changer cela si vous n'utilisez pas allSteps.
        for (int i = 0; i < step; i++) {
            m_writer.write("===== STEP " + i + " ===== \n" + allSteps.get("_step" + i).toString());
        }
        return null;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        //node.childrenAccept(this, data);
        //return null;

        HashSet<String> previous = (HashSet<String>) data;
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            previous = (HashSet<String>) node.jjtGetChild(i).jjtAccept(this, previous);
        }
        return previous;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        // TODO: Définition des statements, cette fonction est importante pour l'identification des "step".
        //node.childrenAccept(this, previous_step);
        //return null;

        String current = genStep();
        HashSet<String> previous = (HashSet<String>) data;
        StepStatus currentStatus = new StepStatus();
        currentStatus.PRED.addAll(previous);
        allSteps.put(current, currentStatus);

        for (String step : previous) {
            StepStatus previousStatus = allSteps.get(step);
            previousStatus.SUCC.add(current);
        }
        return node.jjtGetChild(0).jjtAccept(this, current);
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        // TODO: Cas IfStmt.
        //  Attention au cas de "if cond stmt" (sans else) qui est la difficulté ici...
        //node.childrenAccept(this, data);
        //return null;

        String current = (String) data;
        StepStatus status = allSteps.get(current);
        HashSet<String> previous = new HashSet<>();
        HashSet<String> next = new HashSet<>();

        previous.add(current);
        current_ref_ids.clear();
        node.jjtGetChild(0).jjtAccept(this, null);
        status.REF.addAll(current_ref_ids);

        HashSet<String> nextBody = (HashSet<String>) node.jjtGetChild(1).jjtAccept(this, previous);

        next.addAll(nextBody);
        next.add(current);
        return next;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        // TODO: Cas WhileStmt.
        //  Attention au cas de la condition qui est la difficulté ici...

        //node.childrenAccept(this, data);
        //return null;

        String currentStep = (String) data;
        StepStatus currentStatus = allSteps.get(currentStep);
        HashSet<String> previous = new HashSet<>();
        HashSet<String> last = new HashSet<>();
        last.add(currentStep);

        previous.add(currentStep);
        current_ref_ids.clear();
        node.jjtGetChild(0).jjtAccept(this, null);
        currentStatus.REF.addAll(current_ref_ids);

        HashSet<String> next = (HashSet<String>) node.jjtGetChild(1).jjtAccept(this, previous);

        currentStatus.PRED.addAll(next);

        for (String step : next) {
            StepStatus afterStatus = allSteps.get(step);
            afterStatus.SUCC.add(currentStep);
        }
        return last;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        // TODO: vous avez le cas "DEF" ici... conseil: c'est ici qu'il faut faire ça ;)
        /*for(int i=0; i < node.jjtGetNumChildren(); i++ ) {
            node.jjtGetChild(i).jjtAccept(this,data);
        }
        return null;*/

        String def = ((ASTIdentifier)node.jjtGetChild(0)).getValue();
        current_ref_ids.clear();
        node.jjtGetChild(1).jjtAccept(this, null);

        String currentStep = (String) data;
        StepStatus currentStatus = allSteps.get(currentStep);
        currentStatus.DEF.add(def);
        currentStatus.REF.addAll(current_ref_ids);

        HashSet<String> next = new HashSet<>();
        next.add(currentStep);
        return next;
    }

    @Override
    public Object visit(ASTExpr node, Object data){ return node.jjtGetChild(0).jjtAccept(this, data); }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTNotExpr node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) { return node.jjtGetChild(0).jjtAccept(this, data); }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        // TODO: Ici on a accès au nom des variables
        current_ref_ids.add(node.getValue());
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) { return Integer.toString(node.getValue()); }



    @Override
    public Object visit(ASTSwitchStmt node, Object data) {

        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    /* UTILE POUR VARIABLES VIVES
     * Chaque Set représente un group utile pour l'algorithme.
     * Fonction "toString" utile pour l'impression finale de chaque step.
     */

    private class StepStatus {
        public HashSet<String> REF = new HashSet<String>();
        public HashSet<String> DEF = new HashSet<String>();
        public HashSet<String> IN  = new HashSet<String>();
        public HashSet<String> OUT = new HashSet<String>();

        public HashSet<String> SUCC  = new HashSet<String>();
        public HashSet<String> PRED  = new HashSet<String>();

        public String toString() {
            String buff = "";
            buff += "REF : " + set_ordered(REF) +"\n";
            buff += "DEF : " + set_ordered(DEF) +"\n";
            buff += "IN  : " + set_ordered(IN) +"\n";
            buff += "OUT : " + set_ordered(OUT) +"\n";

            buff += "SUCC: " + set_ordered(SUCC) +"\n";
            buff += "PRED: " + set_ordered(PRED) +"\n";
            buff += "\n";
            return buff;
        }

        public String set_ordered(HashSet<String> s) {
            List<String> list = new ArrayList<String>(s);
            Collections.sort(list);
            return list.toString();
        }
    }

    /*
     * Cette fonction devrait générer les champs IN et OUT.
     * C'est ici que vous appliquez l'algorithme de Variables Vives !
     *
     * Cfr. Algo du cours
     */
    private void compute_IN_OUT() {
        Stack<String> list = new Stack<>();
        ArrayList<String> stop = new ArrayList<>(previous_step);

        for (String node : stop) {
            list.push(node);
        }

        for (StepStatus step : allSteps.values()) {
            step.IN = new HashSet<>();
            step.OUT = new HashSet<>();
        }

        while (!list.empty()) {
            String currentNode = list.pop();
            StepStatus currentStatus = allSteps.get(currentNode);

            for (String succ : currentStatus.SUCC) {
                StepStatus succStatus = allSteps.get(succ);
                currentStatus.OUT.addAll(succStatus.IN);
            }

            HashSet<String> old = (HashSet<String>) currentStatus.IN.clone();
            HashSet<String> newIN = (HashSet<String>) currentStatus.OUT.clone();
            newIN.removeAll(currentStatus.DEF);
            newIN.addAll(currentStatus.REF);
            currentStatus.IN = newIN;

            if (!currentStatus.IN.equals(old)) {
                for (String prev : currentStatus.PRED) {
                    list.push(prev);
                }
            }
        }
    }
}
