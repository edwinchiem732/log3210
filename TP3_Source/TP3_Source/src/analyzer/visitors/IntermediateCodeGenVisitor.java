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

public class IntermediateCodeGenVisitor implements ParserVisitor {

    //le m_writer est un Output_Stream connecter au fichier "result". c'est donc ce qui permet de print dans les fichiers
    //le code généré.
    private final PrintWriter m_writer;

    public IntermediateCodeGenVisitor(PrintWriter writer) {
        m_writer = writer;
    }
    public HashMap<String, VarType> SymbolTable = new HashMap<>();

    private int id = 0;
    private int label = 0;
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
        VarType type;
        ASTIdentifier id = (ASTIdentifier) node.jjtGetChild(0);

        if(node.getValue().equals("bool")) {
            type = VarType.Bool;
        } else {
            type = VarType.Number;
        }
        SymbolTable.put(id.getValue(), type);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        String value = "";

        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            if(i == node.jjtGetNumChildren() - 1){
                value = (String) data;
            }else{
                value = genLabel();
            }
            node.jjtGetChild(i).jjtAccept(this, value);
            if(i < node.jjtGetNumChildren() - 1) {
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
        String value="";
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        value = genLabel();

        node.jjtGetChild(0).jjtAccept(this, value);
        m_writer.println(value);

        BoolLabel labelChild1 = new BoolLabel(genLabel(), data.toString());
        node.jjtGetChild(1).jjtAccept(this, labelChild1);
        m_writer.println(labelChild1.lTrue);

        node.jjtGetChild(3).jjtAccept(this, value);
        node.jjtGetChild(2).jjtAccept(this, value);


        m_writer.println("goto " + value);

        return null;
    }

    /*
    le If Stmt doit vérifier s'il à trois enfants pour savoir s'il s'agit d'un "if-then" ou d'un "if-then-else".
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        String boolLabelTrue = "";
        String boolLabelFalse = "";

        if(node.jjtGetNumChildren() == 2){
            boolLabelTrue = genLabel();
            boolLabelFalse = (String) data;

            node.jjtGetChild(0).jjtAccept(this, new BoolLabel(boolLabelTrue, boolLabelFalse));
            m_writer.println(boolLabelTrue);

            node.jjtGetChild(1).jjtAccept(this, data);
            return null;
        }

        boolLabelTrue  = genLabel();
        boolLabelFalse = genLabel();

        node.jjtGetChild(0).jjtAccept(this, new BoolLabel(boolLabelTrue, boolLabelFalse));
        m_writer.println(boolLabelTrue);

        node.jjtGetChild(1).jjtAccept(this, data);
        m_writer.println("goto " + data);
        m_writer.println(boolLabelFalse);

        node.jjtGetChild(2).jjtAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        String label = genLabel();
        String boolLabelTrue = genLabel();
        String boolLabelFalse= (String)data;

        m_writer.println(label);
        node.jjtGetChild(0).jjtAccept(this, new BoolLabel(boolLabelTrue, boolLabelFalse));

        m_writer.println(boolLabelTrue);
        node.jjtGetChild(1).jjtAccept(this, label);

        m_writer.println("goto " + label);

        return null;
    }


    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        String boolLabelTrue="";
        String boolLabelFalse="";
        String value = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if(SymbolTable.get(value).equals(VarType.Bool)){
            boolLabelTrue = genLabel();
            boolLabelFalse = genLabel();

            BoolLabel newLabel = new BoolLabel(boolLabelTrue,  boolLabelFalse);
            node.childrenAccept(this, newLabel);
            m_writer.println(boolLabelTrue);
            m_writer.println(value + " = 1");
            m_writer.println("goto " + ((String) data));
            m_writer.println( boolLabelFalse);
            m_writer.println(value + " = 0");
        }else{
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
        String child0Value = (String) node.jjtGetChild(0).jjtAccept(this, data);
        String child1Value = (String) node.jjtGetChild(1).jjtAccept(this, data);

        m_writer.println(id + " = " + child0Value + " " + ops.get(0) + " " + child1Value);
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
        String id="" ;
        String value = (String) node.jjtGetChild(0).jjtAccept(this, data);
        for(int i = 0; i < node.getOps().size(); i++) {
            id = genId();
            m_writer.println(id + " =" + " - " + value);
            value = id;
        }
        return value;
    }

    //expression logique
    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        String booleanCase1True = "";
        String booleanCase1False = "";
        String booleanCase2True = "";
        String booleanCase2False= "";

        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }
        String operation = (String) node.getOps().get(0);
        if(operation.equals("&&")) {
            booleanCase1True = genLabel();
            booleanCase1False = ((BoolLabel) data).lFalse;
            booleanCase2True = ((BoolLabel) data).lTrue;
            booleanCase2False = ((BoolLabel) data).lFalse;

            BoolLabel labelChild0 = new BoolLabel(booleanCase1True, booleanCase1False);

            node.jjtGetChild(0).jjtAccept(this, labelChild0);
            m_writer.println(booleanCase1True);

            BoolLabel labelChild1 = new BoolLabel(booleanCase2True, booleanCase2False);
            node.jjtGetChild(1).jjtAccept(this, labelChild1);
        }else if (operation.equals("||")){
            booleanCase1True = ((BoolLabel) data).lTrue;
            booleanCase1False = genLabel();
            booleanCase2True = ((BoolLabel) data).lTrue;
            booleanCase2False = ((BoolLabel) data).lFalse;

            BoolLabel labelChild0 = new BoolLabel(booleanCase1True, booleanCase1False);

            node.jjtGetChild(0).jjtAccept(this, labelChild0);
            m_writer.println(booleanCase1False);

            BoolLabel labelChild1 = new BoolLabel(booleanCase2True, booleanCase2False);
            node.jjtGetChild(1).jjtAccept(this, labelChild1);
        }

        return null;
    }





    @Override
    public Object visit(ASTCompExpr node, Object data) {
        if (node.jjtGetNumChildren() == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        String op = node.getValue();
        String child0Val = (String) node.jjtGetChild(0).jjtAccept(this, data);
        String child1Val = (String) node.jjtGetChild(1).jjtAccept(this, data);

        m_writer.println("if " + child0Val + " " + op + " " + child1Val + " goto " + ((BoolLabel)data).lTrue);
        m_writer.println("goto " + ((BoolLabel)data).lFalse);
        return null;
    }


    /*
    Même si on peut y avoir un grand nombre d'opération, celle-ci s'annullent entre elle.
    il est donc intéressant de vérifier si le nombre d'opération est pair ou impaire.
    Si le nombre d'opération est pair, on peut simplement ignorer ce noeud.
     */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        if(node.getOps().size()%2 == 1) {
            BoolLabel label = new BoolLabel(((BoolLabel)data).lFalse, ((BoolLabel)data).lTrue);
            node.jjtGetChild(0).jjtAccept(this, label);

        }
        return node.jjtGetChild(0).jjtAccept(this, data);
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
        String value = " ";
        if (node.getValue()) {
            value = ((BoolLabel) data).lTrue;
        }else{
            value = ((BoolLabel) data).lFalse;
        }
        m_writer.println("goto " + value);
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

        if(node.jjtGetParent() instanceof ASTAssignStmt==false && this.SymbolTable.get(id).equals(VarType.Bool)){
            m_writer.println("if " + id + " == 1 goto " + ((BoolLabel) data).lTrue);
            m_writer.println("goto " + ((BoolLabel) data).lFalse);
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
        m_writer.println("goto " + start);

        String switchVar = (String)node.jjtGetChild(0).jjtAccept(this, data);

        String[] labels = new String[node.jjtGetNumChildren()];
        String[] cases = new String[node.jjtGetNumChildren()];

        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            String newLabel = genLabel();
            labels[i] = newLabel;
            m_writer.println(newLabel);
            cases[i] = (String)node.jjtGetChild(i).jjtAccept(this, data);
            m_writer.println("goto " + data);
        }

        m_writer.println(start);
        for (int i = 1; i < node.jjtGetNumChildren() - 1; i++) {
            m_writer.println("if " + switchVar + " == " + cases[i] + " goto " + labels[i]);
        }
        if (node.jjtGetNumChildren() == 2) {
            m_writer.println("if " + switchVar + " == " + cases[1] + " goto " + labels[1]);
        } else {
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
