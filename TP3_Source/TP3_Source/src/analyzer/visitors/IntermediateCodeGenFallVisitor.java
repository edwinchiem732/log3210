package analyzer.visitors;

import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import sun.awt.Symbol;

import java.awt.*;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Vector;


/**
 * Created: 19-02-15
 * Last Changed: 20-10-6
 * Author: Félix Brunet & Doriane Olewicki
 * Modified by: Gérard Akkerhuis
 *
 * Description: Ce visiteur explore l'AST et génère un code intermédiaire.
 */

public class IntermediateCodeGenFallVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter m_writer;

    public IntermediateCodeGenFallVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, VarType> SymbolTable = new HashMap<>();

    private int id = 0;
    private int label = 0;

    private String Fall = "fall";

    /*
    génère une nouvelle variable temporaire qu'il est possible de print
    À noté qu'il serait possible de rentrer en conflit avec un nom de variable définit dans le programme.
    Par simplicité, dans ce tp, nous ne concidérerons pas cette possibilité, mais il faudrait un générateur de nom de
    variable beaucoup plus robuste dans un vrai compilateur.
     */
    private String genId() {
        return "_t" + id++;
    }

    //génère un nouveau Label qu'il est possible de print.
    private String genLabel() {
        return "_L" + label++;
    }

    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data)  {
        String label = genLabel();
        node.childrenAccept(this, label);
        m_writer.println(label);
        return null;
    }

    /*
    Code fournis pour remplir la table de symbole.
    Les déclarations ne sont plus utile dans le code à trois adresse.
    elle ne sont donc pas concervé.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);
        VarType t;
        if (node.getValue().equals("bool") == true) {
            t = VarType.Bool;
        }
        else {
            t = VarType.Number;
        }
        SymbolTable.put(id.getValue(), t);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        String value;
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            if (i == node.jjtGetNumChildren() - 1) {
                value = (String) data;
            }
            else {
                value = genLabel();
            }

            node.jjtGetChild(i).jjtAccept(this, value);

            if (i < node.jjtGetNumChildren() - 1) {
                m_writer.println(value);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTForStmt node, Object data) {
        String caseLabel = "";

        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        caseLabel = genLabel();

        node.jjtGetChild(0).jjtAccept(this, caseLabel);
        m_writer.println(caseLabel);

        IntermediateCodeGenFallVisitor.BoolLabel label = new IntermediateCodeGenFallVisitor.BoolLabel(genLabel(), (String) data);

        node.jjtGetChild(1).jjtAccept(this, label);
        m_writer.println(label.lTrue);

        node.jjtGetChild(3).jjtAccept(this, caseLabel);
        node.jjtGetChild(2).jjtAccept(this, caseLabel);
        m_writer.println("goto " + caseLabel);

        return null;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        String booleanTrue = "";
        String booleanFalse = "";

        if (node.jjtGetNumChildren() == 2) {
            booleanTrue = Fall;
            booleanFalse = (String) data;

            node.jjtGetChild(0).jjtAccept(this, new IntermediateCodeGenFallVisitor.BoolLabel(booleanTrue, booleanFalse));
            node.jjtGetChild(1).jjtAccept(this, data);
            return null;
        }

        booleanTrue  = Fall;
        booleanFalse = genLabel();

        node.jjtGetChild(0).jjtAccept(this, new IntermediateCodeGenFallVisitor.BoolLabel(booleanTrue, booleanFalse));
        node.jjtGetChild(1).jjtAccept(this, data);

        m_writer.println("goto " + data);
        m_writer.println(booleanFalse);

        node.jjtGetChild(2).jjtAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String booleanTrue = Fall;
        String booleanFalse = (String) data;

        String label = genLabel();

        m_writer.println(label);

        node.jjtGetChild(0).jjtAccept(this, new IntermediateCodeGenFallVisitor.BoolLabel(booleanTrue, booleanFalse));
        node.jjtGetChild(1).jjtAccept(this, label);

        m_writer.println("goto " + label);

        return null;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String trueLabel = "";
        String falseLabel = "";
        String value = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if (SymbolTable.get(value).equals(VarType.Bool)) {
            trueLabel = Fall;
            falseLabel = genLabel();
            IntermediateCodeGenFallVisitor.BoolLabel label = new IntermediateCodeGenFallVisitor.BoolLabel(trueLabel, falseLabel);

            node.childrenAccept(this, label);

            m_writer.println(value + " = 1");
            m_writer.println("goto " + data);
            m_writer.println(falseLabel);
            m_writer.println(value + " = 0");
        }
        else {
            String other = (String) node.jjtGetChild(1).jjtAccept(this, data);
            m_writer.println(value + " = " + other);
        }

        return null;
    }



    @Override
    public Object visit(ASTExpr node, Object data){
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    //Expression arithmétique
    /*
    Les expressions arithmétique add et mult fonctionne exactement de la même manière. c'est pourquoi
    il est plus simple de remplir cette fonction une fois pour avoir le résultat pour les deux noeuds.

    On peut bouclé sur "ops" ou sur node.jjtGetNumChildren(),
    la taille de ops sera toujours 1 de moins que la taille de jjtGetNumChildren
     */


    public Object codeExtAddMul(SimpleNode node, Object data, Vector<String> ops) {
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        String id = genId();
        String value1 = (String) node.jjtGetChild(0).jjtAccept(this, data);
        String value2 = (String) node.jjtGetChild(1).jjtAccept(this, data);

        m_writer.println(id + " = " + value1 + " " + ops.get(0) + " " + value2);
        return id;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        return codeExtAddMul(node, data, node.getOps());
    }

    //UnaExpr est presque pareil au deux précédente. la plus grosse différence est qu'il ne va pas
    //chercher un deuxième noeud enfant pour avoir une valeur puisqu'il s'agit d'une opération unaire.
    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        String label = "";
        String value = (String) node.jjtGetChild(0).jjtAccept(this, data);

        for (int i = 0; i < node.getOps().size(); i++) {
            label = genId();
            m_writer.println(label + " =" + " - " + value);
            value = label;
        }
        return value;
    }

    //expression logique
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        String operation = "";
        String booleanCase1True = "";
        String booleanCase1False = "";
        String booleanCase2True = "";
        String booleanCase2False = "";

        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        operation = (String) node.getOps().get(0);

        if (operation.equals("&&")) {

            booleanCase1True = Fall;

            if (((IntermediateCodeGenFallVisitor.BoolLabel) data).lFalse.equals(Fall)) {
                booleanCase1False = genLabel();
            }
            else {
                booleanCase1False = ((IntermediateCodeGenFallVisitor.BoolLabel) data).lFalse;
            }
            booleanCase2True = booleanCase1True;
            booleanCase2False = ((IntermediateCodeGenFallVisitor.BoolLabel) data).lFalse;

            IntermediateCodeGenFallVisitor.BoolLabel labelChild0 = new IntermediateCodeGenFallVisitor.BoolLabel(booleanCase1True, booleanCase1False);
            node.jjtGetChild(0).jjtAccept(this, labelChild0);

            IntermediateCodeGenFallVisitor.BoolLabel labelChild1 = new BoolLabel(booleanCase2True, booleanCase2False);
            node.jjtGetChild(1).jjtAccept(this, labelChild1);

            if (((IntermediateCodeGenFallVisitor.BoolLabel)data).lFalse.equals(Fall)) {
                m_writer.println(booleanCase1False);
            }
        }

        else {
            if (((IntermediateCodeGenFallVisitor.BoolLabel) data).lTrue.equals(Fall)) {
                booleanCase1True = genLabel();
            }
            else {
                booleanCase1True = ((IntermediateCodeGenFallVisitor.BoolLabel) data).lTrue;
            }

            booleanCase1False = Fall;

            booleanCase2True = ((IntermediateCodeGenFallVisitor.BoolLabel) data).lTrue;
            booleanCase2False = ((IntermediateCodeGenFallVisitor.BoolLabel) data).lFalse;

            IntermediateCodeGenFallVisitor.BoolLabel labelChild0 = new IntermediateCodeGenFallVisitor.BoolLabel(booleanCase1True, booleanCase1False);
            node.jjtGetChild(0).jjtAccept(this, labelChild0);

            IntermediateCodeGenFallVisitor.BoolLabel labelChild1 = new IntermediateCodeGenFallVisitor.BoolLabel(booleanCase2True, booleanCase2False);
            node.jjtGetChild(1).jjtAccept(this, labelChild1);

            if (((IntermediateCodeGenFallVisitor.BoolLabel) data).lTrue.equals(Fall)) {
                m_writer.println(booleanCase1True);
            }
        }

        return null;
    }





    @Override
    public Object visit(ASTCompExpr node, Object data) {
        String boolLabelTrue = "";
        String boolLabelFalse = "";
        String op = node.getValue();
        String child0Val = "";
        String child1Val = "";

        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        boolLabelTrue= ((BoolLabel) data).lTrue;
        boolLabelFalse = ((BoolLabel) data).lFalse;
        child0Val = (String) node.jjtGetChild(0).jjtAccept(this, data);
        child1Val = (String) node.jjtGetChild(1).jjtAccept(this, data);

        if (boolLabelTrue.equals(Fall) == false && boolLabelFalse.equals(Fall) == false) {
            m_writer.println("if " + child0Val + " " + op + " " + child1Val + " goto " + boolLabelTrue);
            m_writer.println("goto " + boolLabelFalse);
        }
        if (boolLabelTrue.equals(Fall) == false && boolLabelFalse.equals(Fall)) {
            m_writer.println("if " + child0Val + " " + op + " " + child1Val + " goto " + boolLabelTrue);
        }
        if (boolLabelTrue.equals(Fall) && boolLabelFalse.equals(Fall) == false) {
            m_writer.println("ifFalse " + child0Val + " " + op + " " + child1Val + " goto " + boolLabelFalse);
        }
        else {
            m_writer.println("error");
        }
        return null;
    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if (node.getOps().size() % 2 == 1) {
            IntermediateCodeGenFallVisitor.BoolLabel label = new IntermediateCodeGenFallVisitor.BoolLabel(((IntermediateCodeGenFallVisitor.BoolLabel)data).lFalse,
                    ((IntermediateCodeGenFallVisitor.BoolLabel)data).lTrue);
            node.jjtGetChild(0).jjtAccept(this, label);
        }
        String child = (String) node.jjtGetChild(0).jjtAccept(this, data);
        return child;
    }

    @Override
    public Object visit(ASTGenValue node, Object data) {
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    /*
    BoolValue ne peut pas simplement retourné sa valeur à son parent contrairement à GenValue et IntValue,
    Il doit plutôt généré des Goto direct, selon sa valeur.
     */
    @Override
    public Object visit(ASTBoolValue node, Object data) {
        String value = "" ;

        if (node.getValue() == true) {
            if (((BoolLabel) data).lTrue.equals(Fall) == false) {
                value = ((BoolLabel) data).lTrue;
                m_writer.println("goto " + ((BoolLabel)data).lTrue);
            }
        }
        else {
            if (((BoolLabel) data).lFalse.equals(Fall) == false) {
                value = ((BoolLabel) data).lFalse;
                m_writer.println("goto " + ((BoolLabel)data).lFalse);
            }
        }
        return value;
    }


    /*
    si le type de la variable est booléenne, il faudra généré des goto ici.
    le truc est de faire un "if value == 1 goto Label".
    en effet, la structure "if valeurBool goto Label" n'existe pas dans la syntaxe du code à trois adresse.
     */
    @Override
    public Object visit(ASTIdentifier node, Object data) {
        String id = node.getValue();

        if (!(node.jjtGetParent() instanceof ASTAssignStmt) && this.SymbolTable.get(id).equals(VarType.Bool)) {
            String l_true = ((BoolLabel) data).lTrue;
            String l_false = ((BoolLabel) data).lFalse;

            if ((!l_true.equals(Fall)) && (!l_false.equals(Fall))) {
                m_writer.println("if " + id + " == 1 goto " + l_true);
                m_writer.println("goto " + l_false);
            }
            else if ((l_true.equals(Fall)) && (!l_false.equals(Fall))) {
                m_writer.println("ifFalse " + id + " == 1 goto " + l_false);
            }
            else if ((!l_true.equals(Fall)) && (l_false.equals(Fall))) {
                m_writer.println("if " + id + " == 1 goto " + l_true);
            }
            else {
                m_writer.println("error");
            }
            return null;
        }

        return id;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return Integer.toString(node.getValue());
    }


    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        String start = genLabel();
        String switchVar = (String)node.jjtGetChild(0).jjtAccept(this, data);
        String[] labels = new String[node.jjtGetNumChildren()];
        String[] cases = new String[node.jjtGetNumChildren()];

        m_writer.println("goto " + start);

        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            String label = genLabel();
            labels[i] = label;
            m_writer.println(label);
            cases[i] = (String)node.jjtGetChild(i).jjtAccept(this, data);
            m_writer.println("goto " + data);
        }

        m_writer.println(start);

        for (int i = 1; i < node.jjtGetNumChildren() - 1; i++) {
            m_writer.println("if " + switchVar + " == " + cases[i] + " goto " + labels[i]);
        }
        if (node.jjtGetNumChildren() == 2) {
            m_writer.println("if " + switchVar + " == " + cases[1] + " goto " + labels[1]);
        }
        else {
            m_writer.println("goto " + labels[node.jjtGetNumChildren() - 1]);
        }

        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        String value = (String)node.jjtGetChild(0).jjtAccept(this, data);
        node.jjtGetChild(1).jjtAccept(this, data);
        return value;
    }

    @Override
    public Object visit(ASTDefaultStmt node, Object data) {
        node.jjtGetChild(0).jjtAccept(this, data);
        return null;
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number
    }

    //utile surtout pour envoyé de l'informations au enfant des expressions logiques.
    private class BoolLabel {
        public String lTrue = null;
        public String lFalse = null;

        public BoolLabel(String t, String f) {
            lTrue = t;
            lFalse = f;
        }
    }





}
