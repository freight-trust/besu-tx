---
title: Controller Runtime Client API
linkTitle: Controller Runtime Client API
weight: 10
---

## Overview

The [`controller-runtime`][repo-controller-runtime] library provides various abstractions to watch and reconcile resources in a Kubernetes cluster via CRUD (Create, Update, Delete, as well as Get and List in this case) operations. Operators use at least one controller to perform a coherent set of tasks within a cluster, usually through a combination of CRUD operations. The Operator SDK uses controller-runtime's [Client][doc-client-client] interface, which provides the interface for these operations.

controller-runtime defines several interfaces used for cluster interaction:

- `client.Client`: implementers perform CRUD operations on a Kubernetes cluster.
- `manager.Manager`: manages shared dependencies, such as Caches and Clients.
- `reconcile.Reconciler`: compares provided state with actual cluster state and updates the cluster on finding state differences using a Client.

Clients are the focus of this document. A separate document will discuss Managers.

## Client Usage

### Default Client

The SDK relies on a `manager.Manager` to create a `client.Client` interface that performs Create, Update, Delete, Get, and List operations within a `reconcile.Reconciler`'s Reconcile function. The SDK will generate code to create a Manager, which holds a Cache and a Client to be used in CRUD operations and communicate with the API server. By default a Controller's Reconciler will be populated with the Manager's Client which is a [split-client][doc-split-client].

`controllers/<kind>_controller.go`:

```Go
import ctrl "sigs.k8s.io/controller-runtime"

func (r *KindReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&cachev1alpha1.Kind{}).
		Owns(&appsv1.Deployment{}).
		Complete(r)
}

type KindReconciler struct {
    // Populated above from a manager.Manager.
    client.Client
    Log    logr.Logger
    Scheme *runtime.Scheme
}
```

A split client reads (Get and List) from the Cache and writes (Create, Update, Delete) to the API server. Reading from the Cache significantly reduces request load on the API server; as long as the Cache is updated by the API server, read operations are eventually consistent.

### Non-default Client

An operator developer may wish to create their own Client that serves read requests(Get List) from the API server instead of the cache, for example. controller-runtime provides a [constructor][doc-client-constr] for Clients:

```Go
// New returns a new Client using the provided config and Options.
func New(config *rest.Config, options client.Options) (client.Client, error)
```

`client.Options` allow the caller to specify how the new Client should communicate with the API server.

```Go
// Options are creation options for a Client
type Options struct {
    // Scheme, if provided, will be used to map go structs to GroupVersionKinds
    Scheme *runtime.Scheme

    // Mapper, if provided, will be used to map GroupVersionKinds to Resources
    Mapper meta.RESTMapper
}
```

Example:

```Go
import (
    "sigs.k8s.io/controller-runtime/pkg/client/config"
    "sigs.k8s.io/controller-runtime/pkg/client"
)

cfg, err := config.GetConfig()
...
c, err := client.New(cfg, client.Options{})
...
```

**Note**: defaults are set by `client.New` when Options are empty. The default [scheme][code-scheme-default] will have the [core][doc-k8s-core] Kubernetes resource types registered. The caller _must_ set a scheme that has custom operator types registered for the new Client to recognize these types.

Creating a new Client is not usually necessary nor advised, as the default Client is sufficient for most use cases.

### Reconcile and the Client API

A Reconciler implements the [`reconcile.Reconciler`][doc-reconcile-reconciler] interface, which exposes the Reconcile method. Reconcilers are added to a corresponding Controller for a Kind; Reconcile is called in response to cluster or external Events, with a `reconcile.Request` object argument, to read and write cluster state by the Controller, and returns a `reconcile.Result`. SDK Reconcilers have access to a Client in order to make Kubernetes API calls.

```Go
// KindReconciler reconciles a Kind object
type KindReconciler struct {
    // client, initialized using mgr.Client() above, is a split client
    // that reads objects from the cache and writes to the apiserver
    client.Client

    Log    logr.Logger

    // scheme defines methods for serializing and deserializing API objects,
    // a type registry for converting group, version, and kind information
    // to and from Go schemas, and mappings between Go schemas of different
    // versions. A scheme is the foundation for a versioned API and versioned
    // configuration over time.
    Scheme *runtime.Scheme
}

// Reconcile watches for Events and reconciles cluster state with desired
// state defined in the method body.
// The Controller will requeue the Request to be processed again if an error
// is non-nil or Result.Requeue is true, otherwise upon completion it will
// remove the work from the queue.
func (r *KindReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error)
```

Reconcile is where Controller business logic lives, i.e. where Client API calls are made via `KindReconciler.client`. A `client.Client` implementer performs the following operations:

#### Get

```Go
// Get retrieves an API object for a given object key from the Kubernetes cluster
// and stores it in obj.
func (c Client) Get(ctx context.Context, key ObjectKey, obj runtime.Object) error
```

**Note**: An `ObjectKey` is simply a `client` package alias for [`types.NamespacedName`][doc-types-nsname].

Example:

```Go
import (
    "context"
    "github.com/example-org/app-operator/pkg/apis/cache/v1alpha1"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    app := &v1alpha1.App{}
    ctx := context.TODO()
    err := r.Get(ctx, request.NamespacedName, app)

    ...
}
```

#### List

```Go
// List retrieves a list of objects for a given namespace and list options
// and stores the list in obj.
func (c Client) List(ctx context.Context, list runtime.Object, opts ...client.ListOption) error
```

A `client.ListOption` is an interface that sets [`client.ListOptions`][list-options] fields. A `client.ListOption` is created by using one of the provided implementations: [`MatchingLabels`][matching-labels], [`MatchingFields`][matching-fields], [`InNamespace`][in-namespace].

Example:

```Go
import (
    "context"
    "fmt"
    "k8s.io/api/core/v1"
    "sigs.k8s.io/controller-runtime/pkg/client"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    // Return all pods in the request namespace with a label of `app=<name>`
    // and phase `Running`.
    podList := &v1.PodList{}
    opts := []client.ListOption{
        client.InNamespace(request.NamespacedName.Namespace),
        client.MatchingLabels{"app": request.NamespacedName.Name},
        client.MatchingFields{"status.phase": "Running"},
    }
    ctx := context.TODO()
    err := r.List(ctx, podList, opts...)

    ...
}
```

[list-options]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#ListOptions
[matching-labels]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#MatchingLabels
[matching-fields]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#MatchingFields
[in-namespace]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#InNamespace

#### Create

```Go
// Create saves the object obj in the Kubernetes cluster.
// Returns an error
func (c Client) Create(ctx context.Context, obj runtime.Object, opts ...client.CreateOption) error
```

A `client.CreateOption` is an interface that sets [`client.CreateOptions`][create-options] fields. A `client.CreateOption` is created by using one of the provided implementations: [`DryRunAll`][dry-run-all], [`ForceOwnership`][force-ownership]. Generally these options are not needed.

Example:

```Go
import (
    "context"
    "k8s.io/api/apps/v1"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    app := &v1.Deployment{ // Any cluster object you want to create.
        ...
    }
    ctx := context.TODO()
    err := r.Create(ctx, app)

    ...
}
```

[create-options]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#CreateOptions

#### Update

```Go
// Update updates the given obj in the Kubernetes cluster. obj must be a
// struct pointer so that obj can be updated with the content returned
// by the API server. Update does *not* update the resource's status
// subresource
func (c Client) Update(ctx context.Context, obj runtime.Object, opts ...client.UpdateOption) error
```

A `client.UpdateOption` is an interface that sets [`client.UpdateOptions`][update-options] fields. A `client.UpdateOption` is created by using one of the provided implementations: [`DryRunAll`][dry-run-all], [`ForceOwnership`][force-ownership]. Generally these options are not needed.

Example:

```Go
import (
    "context"
    "k8s.io/api/apps/v1"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    dep := &v1.Deployment{}
    err := r.Get(context.TODO(), request.NamespacedName, dep)

    ...

    ctx := context.TODO()
    dep.Spec.Selector.MatchLabels["is_running"] = "true"
    err := r.Update(ctx, dep)

    ...
}
```

[update-options]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#UpdateOptions

#### Patch

```Go
// Patch patches the given obj in the Kubernetes cluster. obj must be a
// struct pointer so that obj can be updated with the content returned by the Server.
func (c Client) Patch(ctx context.Context, obj runtime.Object, patch client.Patch, opts ...client.UpdateOption) error
```

A `client.PatchOption` is an interface that sets [`client.PatchOptions`][patch-options] fields. A `client.PatchOption` is created by using one of the provided implementations: [`DryRunAll`][dry-run-all], [`ForceOwnership`][force-ownership]. Generally these options are not needed.

Example:

```Go
import (
    "context"
    "k8s.io/api/apps/v1"
    "sigs.k8s.io/controller-runtime/pkg/client"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    dep := &v1.Deployment{}
    err := r.Get(context.TODO(), request.NamespacedName, dep)

    ...

    ctx := context.TODO()
    // A merge patch will preserve other fields modified at runtime.
    patch := client.MergeFrom(dep.DeepCopy())
    dep.Spec.Selector.MatchLabels["is_running"] = "true"
    err := r.Patch(ctx, dep, patch)

    ...
}
```

[patch-options]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#PatchOption
[dry-run-all]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#DryRunAll
[force-ownership]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#ForceOwnership

##### Updating Status Subresource

When updating the [status subresource][cr-status-subresource] from the client, the [`StatusWriter`][status-writer] must be used. The status subresource is retrieved with `Status()` and updated with `Update()` or patched with `Patch()`.

`Update()` takes variadic `client.UpdateOption`'s, and `Patch()` takes variadic `client.PatchOption`'s. See [`Client.Update()`](#update) and [`Client.Patch()`](#patch) for more details. Generally these options are not needed.

##### Status

```Go
// Status() returns a StatusWriter object that can be used to update the
// object's status subresource
func (c Client) Status() (client.StatusWriter, error)
```

Example:

```Go
import (
    "context"
    cachev1alpha1 "github.com/example-inc/memcached-operator/pkg/apis/cache/v1alpha1"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    ctx := context.TODO()
    mem := &cachev1alpha1.Memcached{}
    err := r.Get(ctx, request.NamespacedName, mem)

    ...

    // Update
    mem.Status.Nodes = []string{"pod1", "pod2"}
    err := r.Status().Update(ctx, mem)

    ...

    // Patch
    patch := client.MergeFrom(mem.DeepCopy())
    mem.Status.Nodes = []string{"pod1", "pod2", "pod3"}
    err := r.Status().Patch(ctx, mem, patch)

    ...
}
```

[status-writer]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#StatusWriter

#### Delete

```Go
// Delete deletes the given obj from Kubernetes cluster.
func (c Client) Delete(ctx context.Context, obj runtime.Object, opts ...client.DeleteOption) error
```

A `client.DeleteOption` is an interface that sets [`client.DeleteOptions`][delete-opts] fields. A `client.DeleteOption` is created by using one of the provided implementations: [`GracePeriodSeconds`][grace-period-seconds], [`Preconditions`][preconditions], [`PropagationPolicy`][propagation-policy].

Example:

```Go
import (
    "context"
    "k8s.io/api/core/v1"
    "sigs.k8s.io/controller-runtime/pkg/client"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    pod := &v1.Pod{}
    err := r.Get(context.TODO(), request.NamespacedName, pod)

    ...

    ctx := context.TODO()
    if pod.Status.Phase == v1.PodUnknown {
        // Delete the pod after 5 seconds.
        err := r.Delete(ctx, pod, client.GracePeriodSeconds(5))
        ...
    }

    ...
}
```

[delete-opts]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#DeleteOptions
[grace-period-seconds]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#GracePeriodSeconds
[preconditions]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#Preconditions
[propagation-policy]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#PropagationPolicy

#### DeleteAllOf

```Go
// DeleteAllOf deletes all objects of the given type matching the given options.
func (c Client) DeleteAllOf(ctx context.Context, obj runtime.Object, opts ...client.DeleteAllOfOption) error
```

A `client.DeleteAllOfOption` is an interface that sets [`client.DeleteAllOfOptions`][deleteallof-opts] fields. A `client.DeleteAllOfOption` wraps a [`client.ListOption`](#list) and [`client.DeleteOption`](#delete).

Example:

```Go
import (
    "context"
    "fmt"
    "k8s.io/api/core/v1"
    "sigs.k8s.io/controller-runtime/pkg/client"
    ctrl "sigs.k8s.io/controller-runtime"
)

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Request, error) {
    ...

    // Delete all pods in the request namespace with a label of `app=<name>`
    // and phase `Failed`.
    pod := &v1.Pod{}
    opts := []client.DeleteAllOfOption{
        client.InNamespace(request.NamespacedName.Namespace),
        client.MatchingLabels{"app", request.NamespacedName.Name},
        client.MatchingFields{"status.phase": "Failed"},
        client.GracePeriodSeconds(5),
    }
    ctx := context.TODO()
    err := r.DeleteAllOf(ctx, pod, opts...)

    ...
}
```

[deleteallof-opts]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/client#DeleteAllOfOptions

### Example usage

```Go
import (
    "context"
    "reflect"

    appv1alpha1 "github.com/example-org/app-operator/api/v1alpha1"

    appsv1 "k8s.io/api/apps/v1"
    corev1 "k8s.io/api/core/v1"
    "k8s.io/apimachinery/pkg/api/errors"
    metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
    "k8s.io/apimachinery/pkg/labels"
    "k8s.io/apimachinery/pkg/runtime"
    "k8s.io/apimachinery/pkg/types"
    "sigs.k8s.io/controller-runtime/pkg/client"
    "sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
    ctrl "sigs.k8s.io/controller-runtime"
)

type AppReconciler struct {
    client.Client
    Log    logr.Logger
    Scheme *runtime.Scheme
}

func (r *AppReconciler) Reconcile(req ctrl.Request) (ctrl.Result, error) {

    // Fetch the App instance.
    app := &appv1alpha1.App{}
    err := r.Get(context.TODO(), request.NamespacedName, app)
    if err != nil {
        if errors.IsNotFound(err) {
            return ctrl.Result{}, nil
        }
        return ctrl.Result{}, err
    }

    // Check if the deployment already exists, if not create a new deployment.
    found := &appsv1.Deployment{}
    err = r.Get(context.TODO(), types.NamespacedName{Name: app.Name, Namespace: app.Namespace}, found)
    if err != nil {
         if errors.IsNotFound(err) {
            // Define and create a new deployment.
            dep := r.deploymentForApp(app)
            if err = r.Create(context.TODO(), dep); err != nil {
                return ctrl.Result{}, err
            }
            return ctrl.Result{Requeue: true}, nil
        } else {
            return ctrl.Result{}, err
        }
    }

    // Ensure the deployment size is the same as the spec.
    size := app.Spec.Size
    if *found.Spec.Replicas != size {
        found.Spec.Replicas = &size
        if err = r.Update(context.TODO(), found); err != nil {
            return ctrl.Result{}, err
        }
        return ctrl.Result{Requeue: true}, nil
    }

    // Update the App status with the pod names.
    // List the pods for this app's deployment.
    podList := &corev1.PodList{}
    listOpts := []client.ListOption{
        client.InNamespace(app.Namespace),
        client.MatchingLabels(labelsForApp(app.Name)),
    }
    if err = r.List(context.TODO(), podList, listOpts...); err != nil {
        return ctrl.Result{}, err
    }

    // Update status.Nodes if needed.
    podNames := getPodNames(podList.Items)
    if !reflect.DeepEqual(podNames, app.Status.Nodes) {
        app.Status.Nodes = podNames
        if err := r.Status().Update(context.TODO(), app); err != nil {
            return ctrl.Result{}, err
        }
    }

    return ctrl.Request{}, nil
}

// deploymentForApp returns a app Deployment object.
func (r *KindReconciler) deploymentForApp(m *appv1alpha1.App) *appsv1.Deployment {
    lbls := labelsForApp(m.Name)
    replicas := m.Spec.Size

    dep := &appsv1.Deployment{
        ObjectMeta: metav1.ObjectMeta{
            Name:      m.Name,
            Namespace: m.Namespace,
        },
        Spec: appsv1.DeploymentSpec{
            Replicas: &replicas,
            Selector: &metav1.LabelSelector{
                MatchLabels: lbls,
            },
            Template: corev1.PodTemplateSpec{
                ObjectMeta: metav1.ObjectMeta{
                    Labels: lbls,
                },
                Spec: corev1.PodSpec{
                    Containers: []corev1.Container{{
                        Image:   "app:alpine",
                        Name:    "app",
                        Command: []string{"app", "-a=64", "-b"},
                        Ports: []corev1.ContainerPort{{
                            ContainerPort: 10000,
                            Name:          "app",
                        }},
                    }},
                },
            },
        },
    }

    // Set App instance as the owner and controller.
    // NOTE: calling SetControllerReference, and setting owner references in
    // general, is important as it allows deleted objects to be garbage collected.
    controllerutil.SetControllerReference(m, dep, r.scheme)
    return dep
}

// labelsForApp creates a simple set of labels for App.
func labelsForApp(name string) map[string]string {
    return map[string]string{"app_name": "app", "app_cr": name}
}
```

[repo-controller-runtime]: https://github.com/kubernetes-sigs/controller-runtime
[doc-client-client]: https://godoc.org/github.com/kubernetes-sigs/controller-runtime/pkg/client#Client
[doc-split-client]: https://godoc.org/github.com/kubernetes-sigs/controller-runtime/pkg/client#DelegatingClient
[doc-client-constr]: https://godoc.org/github.com/kubernetes-sigs/controller-runtime/pkg/client#New
[code-scheme-default]: https://github.com/kubernetes-sigs/controller-runtime/blob/master/pkg/client/client.go#L51
[doc-k8s-core]: https://godoc.org/k8s.io/api/core/v1
[doc-reconcile-reconciler]: https://godoc.org/github.com/kubernetes-sigs/controller-runtime/pkg/reconcile#Reconciler
[doc-osdk-handle]: https://github.com/operator-framework/operator-sdk/blob/master/design/milestone-0.0.2/action-api.md#handler
[doc-types-nsname]: https://godoc.org/k8s.io/apimachinery/pkg/types#NamespacedName
[cr-status-subresource]: https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/#status-subresource
