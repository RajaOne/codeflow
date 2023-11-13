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

public class SearchAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("SearchAction.actionPerformed");
        Project project = e.getData(CommonDataKeys.PROJECT);
        Map<String, Node> nodes = new HashMap<>();

        Set<PsiClassImpl> components = new HashSet<>();
        components.addAll(addComponentAnnotatedClasses(project));
        components.addAll(addBeans(project));
        components.addAll(addAutowiredInterfaces(project));
        // TODO check entry points like pubsub with and mark component as entry point
        // ((PsiMethodImpl)((List)AnnotatedElementsSearch.searchPsiMethods(
        //      JavaPsiFacadeImpl.getInstance(project).findClass("org.springframework.integration.annotation.ServiceActivator", GlobalSearchScope.allScope(project))
        //  , GlobalSearchScope.projectScope(project)).findAll()).get(0))
        //  .getAnnotations()[0].getAttributes().get(0)
        //  .getAttributeName()
        // should be "inputchannel"

        addToNodesAndAddReferences(nodes, components);

        displayNodes(nodes);
    }

    private void addToNodesAndAddReferences(Map<String, Node> nodes, Set<PsiClassImpl> components) {
        components.forEach(clazz -> {
            Node node = Node.newComponent(clazz.getName());
            if (hasTestLikeName(clazz)) {
                node.setTest(true);
            }
            if (Arrays.stream(clazz.getAnnotations())
                    .map(psiAnnotation -> psiAnnotation.resolveAnnotationType().getName())
                    .anyMatch(s -> s.endsWith("Controller"))) {
                node.setController(true);
            }
            if (Arrays.stream(clazz.getAnnotations())
                    .map(psiAnnotation -> psiAnnotation.resolveAnnotationType().getName())
                    .anyMatch(s -> s.endsWith("Configuration"))) {
                node.setConfig(true);
            }
            if (Arrays.stream(clazz.getAnnotations())
                    .map(psiAnnotation -> psiAnnotation.resolveAnnotationType().getName())
                    .anyMatch(s -> s.endsWith("Repository"))) {
                node.setRepository(true);
            }
            if (clazz.isInterface()) {
                node.setInterface(true);
            }
            nodes.put(clazz.getName(), node);
        });
        components.forEach(clazz -> {
            Arrays.stream(clazz.getSupers()).forEach(psiClass -> {
                if (psiClass instanceof PsiClassImpl) {
                    PsiClassImpl superClass = (PsiClassImpl) psiClass;
                    nodes.get(clazz.getName()).getInheritsFrom().add(nodes.get(superClass.getName()));
                }
            });
        });
        components.forEach(clazz -> findReferencesAndAddToNode(nodes, clazz));
    }

    private Set<PsiClassImpl> addAutowiredInterfaces(Project project) {
        List<PsiModifierListOwner> componentInheritors = findAllComponentAnnotations(project);
        Set<PsiClassImpl> allClasses = new HashSet<>();
        componentInheritors.forEach(componentInheritor -> {
            List<PsiClassImpl> classes = findAllClassesWithAnnotation((PsiClass) componentInheritor);
            classes.stream().forEach(clazz -> {
                List<PsiClassImpl> superClasses = Arrays.stream(clazz.getSupers())
                        .filter(psiClass -> psiClass instanceof PsiClassImpl)
                        .map(psiClass -> (PsiClassImpl) psiClass)
                        .toList();
                allClasses.addAll(superClasses);
            });
        });
        PsiClass beanClass = findBeanClass(project);
        AnnotatedElementsSearch.searchPsiMethods(beanClass, GlobalSearchScope.projectScope(project)).findAll().stream()
                .forEach(psiMethod -> {
                    PsiElement element = ((PsiClassReferenceType) psiMethod.getReturnType()).getPsiContext().getReference().resolve();
                    if (element instanceof PsiClassImpl) {
                        PsiClassImpl psiClass = (PsiClassImpl) element;
                        List<PsiClassImpl> supers = Arrays.stream(psiClass.getSupers())
                                .filter(psiClass1 -> psiClass1 instanceof PsiClassImpl)
                                .map(psiClass1 -> (PsiClassImpl) psiClass1)
                                .toList();
                        allClasses.addAll(supers);
                    }
                });
        return allClasses;
    }

    private Set<PsiClassImpl> addComponentAnnotatedClasses(Project project) {
        List<PsiModifierListOwner> componentInheritors = findAllComponentAnnotations(project);
        Set<PsiClassImpl> allClasses = new HashSet<>();
        componentInheritors.forEach(componentInheritor -> {
            List<PsiClassImpl> classes = findAllClassesWithAnnotation((PsiClass) componentInheritor);
            allClasses.addAll(classes);
        });
        return allClasses;
    }

    private Set<PsiClassImpl> addBeans(Project project) {
        PsiClass beanClass = findBeanClass(project);
        Set<PsiClassImpl> allClasses = new HashSet<>();
        AnnotatedElementsSearch.searchPsiMethods(beanClass, GlobalSearchScope.projectScope(project)).findAll().stream()
                .forEach(psiMethod -> {
                    PsiElement element = ((PsiClassReferenceType) psiMethod.getReturnType()).getPsiContext().getReference().resolve();
                    if (element instanceof PsiClassImpl) {
                        PsiClassImpl psiClass = (PsiClassImpl) element;
                        allClasses.add(psiClass);
                    }
                });
        return allClasses;
    }

    private PsiClass findBeanClass(Project project) {
        return JavaPsiFacadeImpl.getInstance(project).findClass("org.springframework.context.annotation.Bean", GlobalSearchScope.allScope(project));
    }

    private void findReferencesAndAddToNode(Map<String, Node> nodes, PsiClassImpl psiClass) {
        List<PsiClass> allReferencesFrom = findAllReferencesFrom(psiClass);

        allReferencesFrom.forEach(fromClass -> {
            if (!nodes.containsKey(fromClass.getName())) {
                if (hasTestLikeName(fromClass)) {
                    nodes.put(fromClass.getName(), Node.newTestClass(fromClass.getName()));
                } else {
                    nodes.put(fromClass.getName(), Node.newClass(fromClass.getName()));
                    Arrays.stream(fromClass.getSupers())
                            .filter(superClass -> superClass instanceof PsiClassImpl)
                            .map(superClass -> (PsiClassImpl) superClass)
                            .filter(superClass -> nodes.containsKey(superClass.getName()))
                            .forEach(superClass -> nodes.get(fromClass.getName()).getInheritsFrom().add(nodes.get(superClass.getName())));
                }
            }
            Node from = nodes.get(fromClass.getName());
            Node to = nodes.get(psiClass.getName());
            to.referencedFrom(from);
        });
    }

    private static boolean hasTestLikeName(PsiClass fromClass) {
        return fromClass.getName().endsWith("Test") ||
                fromClass.getName().endsWith("IT") ||
                fromClass.getName().endsWith("AT") ||
                fromClass.getName().endsWith("E2E");
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
                " edge.blue { fill-color: #009; text-color: #009; }" +
                " edge.inheritance { shape: blob; size: 3px; arrow-shape: none; }"
        );

        nodes.values().forEach(node -> {
            org.graphstream.graph.Node addedNode = graph.addNode(node.getName());
            if (node.isController()) {
                addedNode.setAttribute("ui.class", "green");
            }
            if (node.isTest() || node.isConfig()) {
//                addedNode.setAttribute("ui.class", "gray");
                graph.removeNode(addedNode);
            }
            if (node.isRepository()) {
                addedNode.setAttribute("ui.class", "blue");
            }
            if (node.isComponent()) {
                addedNode.setAttribute("ui.label", node.getName());
            } else {
                addedNode.setAttribute("ui.label", "[" + node.getName() + "]");
            }
            if (node.isInterface()) {
                addedNode.setAttribute("ui.label", "<" + node.getName() + ">");
            }
        });
        nodes.values().forEach(node -> {
            node.getReferencedFrom().forEach(referencedFrom -> {
                if (graph.getNode(referencedFrom.getName()) == null) {
                    return;
                }
                if (graph.getNode(node.getName()) == null) {
                    return;
                }
                Edge addedEdge = graph.addEdge(referencedFrom.getName() + node.getName(),
                        referencedFrom.getName(),
                        node.getName(),
                        true);
                if (referencedFrom.isController() || node.isController()) {
                    addedEdge.setAttribute("ui.class", "green");
                }
                if (referencedFrom.isRepository() || node.isRepository()) {
                    addedEdge.setAttribute("ui.class", "blue");
                }
//                if (referencedFrom.isInterface() || node.isInterface()) {
//                    addedEdge.setAttribute("ui.class", "blue");
//                }
                if (referencedFrom.isTest() || node.isTest()) {
                    addedEdge.setAttribute("ui.class", "gray");
                }
                if (referencedFrom.isConfig() || node.isConfig()) {
                    addedEdge.setAttribute("ui.class", "gray");
                }
                if (referencedFrom.getInheritsFrom().contains(node)) {
                    addedEdge.setAttribute("ui.class", "inheritance");
                }
            });
        });

        Viewer display = graph.display();
        display.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);
    }

    private List<PsiClassImpl> findAllClassesWithAnnotation(PsiClass componentInheritor) {
        return AnnotationTargetsSearch.search(componentInheritor).findAll().stream()
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
