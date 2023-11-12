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
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.view.Viewer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class SearchAction extends AnAction {

    record Node(
            String name,
            boolean isComponent,
            List<Node> referencedFrom,
            boolean test,
            boolean controller,
            boolean config,
            boolean interfaceImpl,
            boolean isInterface
    ) {
        public static Node newComponent(String name) {
            return new Node(name, true, new ArrayList<>(), false, false, false, false, false);
        }

        public static Node newController(String name) {
            return new Node(name, true, new ArrayList<>(), false, true, false, false, false);
        }

        public static Node newConfig(String name) {
            return new Node(name, true, new ArrayList<>(), false, false, true, false, false);
        }

        public static Node newClass(String name) {
            return new Node(name, false, new ArrayList<>(), false, false, false, false, false);
        }

        public static Node newTestClass(String name) {
            return new Node(name, false, new ArrayList<>(), true, false, false, false, false);
        }

        public static Node newInterfaceImpl(String name) {
            return new Node(name, true, new ArrayList<>(), false, false, false, true, false);
        }

        public static Node newInterface(String name) {
            return new Node(name, false, new ArrayList<>(), false, false, false, false, true);
        }

        public Node makeInterfaceImpl() {
            return new Node(name, isComponent, referencedFrom, test, controller, config, true, false);
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
            List<PsiClassImpl> classes = findAllClassesWithAnnotation((PsiClass) componentInheretor);
            classes.stream().forEach(clazz -> {
                List<PsiClassImpl> superClasses = Arrays.stream(clazz.getSupers())
                        .filter(psiClass -> psiClass instanceof PsiClassImpl)
                        .map(psiClass -> (PsiClassImpl) psiClass)
                        .toList();
                if (!superClasses.isEmpty()) {
                    nodes.put(clazz.getName(), nodes.get(clazz.getName()).makeInterfaceImpl());
                }
                superClasses.forEach(psiClass -> allClasses.add(psiClass));
            });
        });
        allClasses.forEach(clazz -> nodes.put(clazz.getName(), Node.newInterface(clazz.getName())));
        allClasses.forEach(clazz -> findReferencesAndAddToNode(nodes, clazz));
    }

    private void addComponentAnnotatedClasses(Project project, Map<String, Node> nodes) {
        List<PsiModifierListOwner> componentInheretors = findAllComponentAnnotations(project);
        Set<PsiClassImpl> allClasses = new HashSet<>();
        componentInheretors.forEach(componentInheretor -> {
            List<PsiClassImpl> classes = findAllClassesWithAnnotation((PsiClass) componentInheretor);
            allClasses.addAll(classes);
        });
        allClasses.forEach(clazz -> {
            if (clazz.getName().endsWith("Test") || clazz.getName().endsWith("IT") || clazz.getName().endsWith("AT")) {
                nodes.put(clazz.getName(), Node.newTestClass(clazz.getName()));
            } else if (Arrays.stream(clazz.getAnnotations())
                    .map(psiAnnotation -> psiAnnotation.resolveAnnotationType().getName())
                    .anyMatch(s -> s.endsWith("Controller"))) {
                nodes.put(clazz.getName(), Node.newController(clazz.getName()));
            } else if (Arrays.stream(clazz.getAnnotations())
                    .map(psiAnnotation -> psiAnnotation.resolveAnnotationType().getName())
                    .anyMatch(s -> s.endsWith("Configuration"))) {
                nodes.put(clazz.getName(), Node.newConfig(clazz.getName()));
            } else {
                nodes.put(clazz.getName(), Node.newComponent(clazz.getName()));
            }
        });
        allClasses.forEach(clazz -> findReferencesAndAddToNode(nodes, clazz));
    }

    private void addBeans(Project project, Map<String, Node> nodes) {
        PsiClass beanClass = findBeanClass(project);
        AnnotatedElementsSearch.searchPsiMethods(beanClass, GlobalSearchScope.projectScope(project)).findAll().stream()
                .forEach(psiMethod -> {
//                    System.out.println(psiMethod.getReturnType());
                    PsiElement element = ((PsiClassReferenceType) psiMethod.getReturnType()).getPsiContext().getReference().resolve();
                    if (element instanceof PsiClassImpl) {
                        PsiClassImpl psiClass = (PsiClassImpl) element;
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
                if (fromClass.getName().endsWith("Test") || fromClass.getName().endsWith("IT") || fromClass.getName().endsWith("AT")) {
                    nodes.put(fromClass.getName(), Node.newTestClass(fromClass.getName()));
                } else {
                    nodes.put(fromClass.getName(), Node.newClass(fromClass.getName()));
                }
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
                " node { text-alignment: at-right; text-background-mode: plain; text-background-color: #FFF9; text-size: 14; }" +
                " node.gray { fill-color: #999; text-color: #999; z-index: 0; text-size: 10; }" +
                " node.green { fill-color: #090; text-color: #090; }" +
                " node.blue { fill-color: #009; text-color: #009; }" +
                " edge { }" +
                " edge.gray { fill-color: #999; text-color: #999; z-index: 0; }" +
                " edge.green { fill-color: #090; text-color: #090; }" +
                " edge.blue { fill-color: #009; text-color: #009; }"
        );

        nodes.values().forEach(node -> {
            org.graphstream.graph.Node addedNode = graph.addNode(node.name());
            if (node.controller()) {
                addedNode.setAttribute("ui.class", "green");
            }
            if (node.test() || node.config()) {
                addedNode.setAttribute("ui.class", "gray");
            }
            if (node.interfaceImpl() || node.isInterface()) {
                addedNode.setAttribute("ui.class", "blue");
            }
            if (node.isComponent()) {
                addedNode.setAttribute("ui.label", node.name());
            } else if (node.isInterface()) {
                addedNode.setAttribute("ui.label", "<" + node.name() + ">");
            } else {
                addedNode.setAttribute("ui.label", "[" + node.name() + "]");
            }
        });
        nodes.values().forEach(node -> {
            node.referencedFrom().forEach(referencedFrom -> {
                Edge addedEdge = graph.addEdge(referencedFrom.name() + node.name(),
                        referencedFrom.name(),
                        node.name(),
                        true);
                if (referencedFrom.test() || node.test()) {
                    addedEdge.setAttribute("ui.class", "gray");
                }
                if (referencedFrom.controller() || node.controller()) {
                    addedEdge.setAttribute("ui.class", "green");
                }
                if (referencedFrom.config() || node.config()) {
                    addedEdge.setAttribute("ui.class", "gray");
                }
                if (referencedFrom.interfaceImpl() || node.interfaceImpl()) {
                    addedEdge.setAttribute("ui.class", "blue");
                }
                if (referencedFrom.isInterface() || node.isInterface()) {
                    addedEdge.setAttribute("ui.class", "blue");
                }
            });
        });

        Viewer display = graph.display();
        display.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);
    }

    private List<PsiClassImpl> findAllClassesWithAnnotation(PsiClass componentInheretor) {
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
