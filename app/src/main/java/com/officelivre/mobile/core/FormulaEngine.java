package com.officelivre.mobile.core;

import java.util.*;
import java.util.regex.*;

/** Avaliador offline de fórmulas, sem executar código externo nem acessar a rede. */
public final class FormulaEngine {
    private static final Pattern RANGE=Pattern.compile("(?i)^([A-Z]+[1-9][0-9]*):([A-Z]+[1-9][0-9]*)$");
    private final SheetData sheet;
    private final Set<String> resolving=new HashSet<>();
    private int depth;
    public FormulaEngine(SheetData sheet) { this.sheet=sheet; }
    public String display(String cell) {
        String raw=sheet.get(cell);
        if(!raw.startsWith("="))return raw;
        try {
            double n=evaluate(cell);
            if(!Double.isFinite(n))return "#NÚM!";
            if(n==Math.rint(n) && Math.abs(n)<1e15)return Long.toString((long)n);
            return String.format(Locale.US,"%.8f",n).replaceAll("0+$","").replaceAll("\\.$","");
        } catch(Cycle ex){return "#CICLO!";}
        catch(ArithmeticException ex){return "#DIV/0!";}
        catch(RuntimeException ex){return "#ERRO";}
    }
    private double evaluate(String reference) {
        if(++depth>150)throw new IllegalArgumentException("Muitas referências encadeadas");
        String cell=reference.toUpperCase(Locale.ROOT);
        if(!resolving.add(cell)){depth--;throw new Cycle();}
        try {
            String raw=sheet.get(cell).trim();
            if(raw.isEmpty())return 0;
            if(raw.startsWith("="))return new Parser(raw.substring(1)).complete();
            try {return Double.parseDouble(raw.replace(',','.'));}
            catch(NumberFormatException ex){return 0;}
        } finally { resolving.remove(cell);depth--; }
    }
    private final class Parser {
        final String s;int pos;
        Parser(String expression){s=expression;}
        void ws(){while(pos<s.length()&&Character.isWhitespace(s.charAt(pos)))pos++;}
        boolean eat(char x){ws();if(pos<s.length()&&s.charAt(pos)==x){pos++;return true;}return false;}
        boolean eat(String x){ws();if(s.startsWith(x,pos)){pos+=x.length();return true;}return false;}
        double complete(){double value=comparison();ws();if(pos!=s.length())throw new IllegalArgumentException("Expressão incompleta");return value;}
        double comparison(){
            double left=additive();
            if(eat("<="))return left<=additive()?1:0;
            if(eat(">="))return left>=additive()?1:0;
            if(eat("<>"))return left!=additive()?1:0;
            if(eat("==")||eat("="))return left==additive()?1:0;
            if(eat("<"))return left<additive()?1:0;
            if(eat(">"))return left>additive()?1:0;
            return left;
        }
        double additive(){double v=product();while(true){if(eat('+'))v+=product();else if(eat('-'))v-=product();else return v;}}
        double product(){double v=unary();while(true){if(eat('*'))v*=unary();else if(eat('/')){double b=unary();if(b==0)throw new ArithmeticException("Divisão por zero");v/=b;}else return v;}}
        double unary(){if(eat('+'))return unary();if(eat('-'))return -unary();return power();}
        double power(){double v=primary();if(eat('^'))v=Math.pow(v,unary());while(eat('%'))v/=100;return v;}
        double primary(){
            if(eat('(')){double n=comparison();if(!eat(')'))throw new IllegalArgumentException("Falta fechar parêntese");return n;}
            ws();int start=pos;
            while(pos<s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos)=='_' ))pos++;
            String name=s.substring(start,pos).toUpperCase(Locale.ROOT);
            if(!name.isEmpty()&&Character.isLetter(name.charAt(0))){
                if(eat('('))return function(name);
                if(name.equals("TRUE")||name.equals("VERDADEIRO"))return 1;
                if(name.equals("FALSE")||name.equals("FALSO"))return 0;
                SheetData.parseRef(name);return evaluate(name);
            }
            pos=start;
            while(pos<s.length()&&(Character.isDigit(s.charAt(pos))||s.charAt(pos)=='.'))pos++;
            if(pos==start)throw new IllegalArgumentException("Número ou referência esperados");
            return Double.parseDouble(s.substring(start,pos));
        }
        double function(String name){
            List<String> args=new ArrayList<>();StringBuilder arg=new StringBuilder();int nesting=0;
            while(true){
                if(pos>=s.length())throw new IllegalArgumentException("Falta ) na função");
                char c=s.charAt(pos++);
                if(c==')'&&nesting==0){if(arg.length()>0||!args.isEmpty())args.add(arg.toString().trim());break;}
                if((c==';'||c==',')&&nesting==0){args.add(arg.toString().trim());arg.setLength(0);continue;}
                if(c=='(')nesting++;if(c==')')nesting--;
                arg.append(c);
            }
            if(name.equals("IF")||name.equals("SE")){
                if(args.size()!=3)throw new IllegalArgumentException("SE requer 3 argumentos");
                return new Parser(args.get(0)).complete()!=0?new Parser(args.get(1)).complete():new Parser(args.get(2)).complete();
            }
            if(name.equals("AND")||name.equals("E")){
                if(args.isEmpty())throw new IllegalArgumentException("Faltam argumentos");
                for(String argument:args)if(new Parser(argument).complete()==0)return 0;
                return 1;
            }
            if(name.equals("OR")||name.equals("OU")){
                if(args.isEmpty())throw new IllegalArgumentException("Faltam argumentos");
                for(String argument:args)if(new Parser(argument).complete()!=0)return 1;
                return 0;
            }
            List<Double> values=new ArrayList<>();
            for(String argument:args){
                Matcher m=RANGE.matcher(argument);
                if(m.matches()){
                    int[] a=SheetData.parseRef(m.group(1)),b=SheetData.parseRef(m.group(2));
                    int r0=Math.min(a[0],b[0]),r1=Math.max(a[0],b[0]);
                    int c0=Math.min(a[1],b[1]),c1=Math.max(a[1],b[1]);
                    if((long)(r1-r0+1)*(c1-c0+1)>10000)throw new IllegalArgumentException("Intervalo excessivo");
                    for(int r=r0;r<=r1;r++)for(int c=c0;c<=c1;c++){
                        String ref=SheetData.ref(r,c),raw=sheet.get(ref).trim();
                        if(raw.startsWith("=")||raw.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))values.add(evaluate(ref));
                    }
                }else if(!argument.isEmpty())values.add(new Parser(argument).complete());
            }
            double sum=0;for(double v:values)sum+=v;
            switch(name){
                case "SUM":case "SOMA":return sum;
                case "AVERAGE":case "AVG":case "MEDIA":case "MÉDIA":return values.isEmpty()?0:sum/values.size();
                case "MIN":return values.isEmpty()?0:Collections.min(values);
                case "MAX":return values.isEmpty()?0:Collections.max(values);
                case "COUNT":case "CONTAR":return values.size();
                case "ABS":return one(name,values,Math::abs);
                case "SQRT":case "RAIZ":return one(name,values,Math::sqrt);
                case "POWER":case "POTENCIA":if(values.size()!=2)throw new IllegalArgumentException("2 argumentos necessários");return Math.pow(values.get(0),values.get(1));
                case "ROUND":case "ARRED":
                    if(values.size()!=2)throw new IllegalArgumentException("2 argumentos necessários");
                    int places=values.get(1).intValue();if(places < -10||places>10)throw new IllegalArgumentException("Casas fora do limite");
                    double factor=Math.pow(10,places);
                    return Math.floor(values.get(0)*factor+0.5)/factor;
                default:throw new IllegalArgumentException("Função desconhecida: "+name);
            }
        }
        private double one(String name,List<Double> values,java.util.function.DoubleUnaryOperator fn){
            if(values.size()!=1)throw new IllegalArgumentException(name+" espera 1 argumento");
            return fn.applyAsDouble(values.get(0));
        }
    }
    private static final class Cycle extends RuntimeException { }
}
