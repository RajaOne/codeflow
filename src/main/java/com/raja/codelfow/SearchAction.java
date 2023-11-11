package com.raja.codelfow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.AnnotationTargetsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SearchAction extends AnAction {

    record Node(String type, String name, boolean isComponent, List<Node> referencedFrom) {
        public static Node newComponent(String name) {
            return new Node("@Component", name, true, new ArrayList<>());
        }

        public static Node newClass(String name) {
            return new Node("@Component", name, false, new ArrayList<>());
        }

        public void referencedFrom(Node node) {
            if (!referencedFrom.contains(node)) {
                referencedFrom.add(node);
            }
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("SearchAction.actionPerformed");
        Project project = e.getData(CommonDataKeys.PROJECT);
        Map<String, Node> nodes = new HashMap<>();

        addComponentAnnotatedClasses(project, nodes);
        addBeans(project, nodes);
        addAutowiredInterfaces(project, nodes);

        displayNodes(nodes);
    }

    private void addAutowiredInterfaces(Project project, Map<String, Node> nodes) {
        List<PsiModifierListOwner> componentInheretors = findAllComponentAnnotations(project);
        Set<PsiClassImpl> allClasses = new HashSet<>();
        componentInheretors.forEach(componentInheretor -> {
            List<PsiClassImpl> classes = findAllClassesWithAnnotiation((PsiClass) componentInheretor);
            classes.stream().forEach(clazz -> {
//                System.out.println("clazz = " + clazz);
                Arrays.stream(clazz.getSupers())
                        .filter(psiClass -> psiClass instanceof PsiClassImpl)
                        .map(psiClass -> (PsiClassImpl) psiClass)
                        .forEach(psiClass -> {
//                            System.out.println("\timplements = " + psiClass);
                            allClasses.add(psiClass);
                        });
            });
//            List<PsiClassImpl> supers = Arrays.stream(classes.get(0).getSupers())
//                    .filter(psiClass -> psiClass instanceof PsiClassImpl)
//                    .map(psiClass -> (PsiClassImpl) psiClass)
//                    .toList();
//            allClasses.addAll(supers);
        });
        allClasses.forEach(clazz -> nodes.put(clazz.getName(), Node.newComponent(clazz.getName())));
        allClasses.forEach(clazz -> findReferencesAndAddToNode(nodes, clazz));
    }

    private void addComponentAnnotatedClasses(Project project, Map<String, Node> nodes) {
        List<PsiModifierListOwner> componentInheretors = findAllComponentAnnotations(project);
        Set<PsiClassImpl> allClasses = new HashSet<>();
        componentInheretors.forEach(componentInheretor -> {
            List<PsiClassImpl> classes = findAllClassesWithAnnotiation((PsiClass) componentInheretor);
            allClasses.addAll(classes);
        });
        allClasses.forEach(clazz -> nodes.put(clazz.getName(), Node.newComponent(clazz.getName())));
        allClasses.forEach(clazz -> findReferencesAndAddToNode(nodes, clazz));
    }

    private void addBeans(Project project, Map<String, Node> nodes) {
        PsiClass beanClass = findBeanClass(project);
        AnnotatedElementsSearch.searchPsiMethods(beanClass, GlobalSearchScope.projectScope(project)).findAll().stream()
                .forEach(psiMethod -> {
//                    System.out.println(psiMethod.getReturnType());
                    if (((PsiClassReferenceType) psiMethod.getReturnType()).getPsiContext().getReference().resolve() instanceof PsiClassImpl) {
                        PsiClassImpl psiClass = (PsiClassImpl) ((PsiClassReferenceType) psiMethod.getReturnType()).getPsiContext().getReference().resolve();
                        nodes.put(psiClass.getName(), Node.newComponent(psiClass.getName()));
                        findReferencesAndAddToNode(nodes, psiClass);
                    }
                });
    }

    private PsiClass findBeanClass(Project project) {
        return JavaPsiFacadeImpl.getInstance(project).findClass("org.springframework.context.annotation.Bean", GlobalSearchScope.allScope(project));
    }

    private void findReferencesAndAddToNode(Map<String, Node> nodes, PsiClassImpl psiClass) {
        List<PsiClass> allReferencesFrom = findAllReferencesFrom(psiClass);

        allReferencesFrom.forEach(fromClass -> {
            if (!nodes.containsKey(fromClass.getName())) {
                nodes.put(fromClass.getName(), Node.newClass(fromClass.getName()));
            }
            Node from = nodes.get(fromClass.getName());
            Node to = nodes.get(psiClass.getName());
            to.referencedFrom(from);
        });
    }

    private void displayNodes(Map<String, Node> nodes) {
        System.setProperty("org.graphstream.ui", "swing");
        Graph graph = new MultiGraph("tutorial 1");
        graph.setAttribute("ui.stylesheet", "graph { }" +
                " node { text-alignment: at-right; text-background-mode: plain; text-background-color: #FFF9; }" +
                " edge { }");

        nodes.values().forEach(node -> {
            if (node.isComponent()) {
                graph.addNode(node.name()).setAttribute("ui.label", node.name());
            } else {
                graph.addNode(node.name()).setAttribute("ui.label", "[" + node.name() + "]");
            }
        });
        nodes.values().forEach(node -> {
            node.referencedFrom().forEach(referencedFrom -> {
                graph.addEdge(referencedFrom.name() + node.name(),
                        referencedFrom.name(),
                        node.name(),
                        true);
            });
        });

        Viewer display = graph.display();
        display.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);
    }

    private List<PsiClassImpl> findAllClassesWithAnnotiation(PsiClass componentInheretor) {
        return AnnotationTargetsSearch.search(componentInheretor).findAll().stream()
                .filter(psiModifierListOwner -> psiModifierListOwner instanceof PsiClassImpl)
                .map(psiModifierListOwner -> (PsiClassImpl) psiModifierListOwner)
                .toList();
    }

    private List<PsiClass> findAllReferencesFrom(PsiClassImpl clazz) {
        return ReferencesSearch.search(clazz).findAll().stream()
                .filter(psiReference -> psiReference instanceof PsiJavaCodeReferenceElementImpl)
                .map(psiReference -> ((PsiJavaCodeReferenceElementImpl) psiReference).getContainingFile())
                .flatMap(psiFile -> Arrays.stream(psiFile.getChildren()))
                .filter(psiElement -> psiElement instanceof PsiClass)
                .distinct()
                .map(psiElement -> (PsiClass) psiElement)
                .toList();
    }

    private List<PsiModifierListOwner> findAllComponentAnnotations(Project project) {
        PsiClass componentClass = JavaPsiFacadeImpl.getInstance(project).findClass("org.springframework.stereotype.Component", GlobalSearchScope.allScope(project));
        List<PsiModifierListOwner> components = new ArrayList<>();
        LinkedList<PsiModifierListOwner> queue = new LinkedList<>();
        queue.addLast(componentClass);

        while (!queue.isEmpty()) {
            PsiModifierListOwner current = queue.removeFirst();
            components.add(current);
            queue.addAll(AnnotationTargetsSearch.search((PsiClass) current).findAll()
                    .stream()
                    .filter(psiModifierListOwner -> psiModifierListOwner instanceof ClsClassImpl)
                    .map(psiModifierListOwner -> (ClsClassImpl) psiModifierListOwner)
                    .filter(clsClassImpl -> clsClassImpl.getStub().isAnnotationType())
                    .toList());
        }

        return components;
    }

    @Override
    public void update(AnActionEvent e) {
//        System.out.println("SearchAction.update");
//        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
//        CaretModel caretModel = editor.getCaretModel();
        e.getPresentation().setEnabledAndVisible(true);
    }

}
