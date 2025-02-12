---
title: Using Predicates for Event Filtering with Operator SDK
linkTitle: Using Predicates for Event Filtering
weight: 30
---

[Events][doc_event] are produced by [Sources][doc_source] assigned to resources a controller is watching. These events are transformed into Requests by [EventHandlers][doc_eventhandler] and passed to `Reconcile()`. [Predicates][doc_predicate] allow controllers to filter events before they are provided to EventHandlers. Filtering is useful because your controller may only want to handle specific types of events. Filtering also helps reduce chattiness with the API server, as `Reconcile()` is only called for events transformed by EventHandlers.

## Predicate types

A Predicate implements the following methods that take an event of a particular type and return true if the event should be processed by `Reconcile()`:

```Go
// Predicate filters events before enqueuing the keys.
type Predicate interface {
  Create(event.CreateEvent) bool
  Delete(event.DeleteEvent) bool
  Update(event.UpdateEvent) bool
  Generic(event.GenericEvent) bool
}

// Funcs implements Predicate.
type Funcs struct {
  CreateFunc func(event.CreateEvent) bool
  DeleteFunc func(event.DeleteEvent) bool
  UpdateFunc func(event.UpdateEvent) bool
  GenericFunc func(event.GenericEvent) bool
}
```

For example, all Create events for any watched resource will be passed to `Funcs.Create()` and filtered out if the method evaluates to `false`. If you do not register a Predicate method for a particular type, events of that type will not be filtered.

All event types contain Kubernetes [metadata][doc_object_metadata] about the object that triggered the event, and the object itself. Predicate logic uses these data to make decisions about what should be filtered. Some event types include other fields pertaining to the semantics of that event. For example, `event.UpdateEvent` includes both old and new metadata and objects:

```Go
type UpdateEvent struct {
  // MetaOld is the ObjectMeta of the Kubernetes Type that was updated (before the update).
  MetaOld v1.Object

  // ObjectOld is the object from the event.
  ObjectOld runtime.Object

  // MetaNew is the ObjectMeta of the Kubernetes Type that was updated (after the update).
  MetaNew v1.Object

  // ObjectNew is the object from the event.
  ObjectNew runtime.Object
}
```

You can find all type definitions in the `event` package [documentation][doc_event].

## Using Predicates

Any number of Predicates can be set for a controller via the builder method `WithEventFilter()`, which will filter an event if any of those Predicates evaluates to `false`. This first example is an implementation of a `memcached-operator` controller that simply filters Delete events on Pods that have been confirmed deleted; the controller receives all Delete events that occur, and we may only care about resources that have not been completely deleted:

```Go
import (
	"context"

	"github.com/go-logr/logr"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/runtime"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/event"
	"sigs.k8s.io/controller-runtime/pkg/predicate"

	cachev1alpha1 "github.com/example-inc/app-operator/apis/cache/v1alpha1"
)

...

func ignoreDeletionPredicate() predicate.Predicate {
	return predicate.Funcs{
		UpdateFunc: func(e event.UpdateEvent) bool {
			// Ignore updates to CR status in which case metadata.Generation does not change
			return e.MetaOld.GetGeneration() != e.MetaNew.GetGeneration()
		},
		DeleteFunc: func(e event.DeleteEvent) bool {
			// Evaluates to false if the object has been confirmed deleted.
			return !e.DeleteStateUnknown
		},
	}
}

func (r *MemcachedReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&cachev1alpha1.Memcached{}).
		Owns(&corev1.Pod{}).
		WithEventFilter(ignoreDeletionPredicate()).
		Complete(r)
}
  ...
}
```

## Use cases

Predicates are not necessary for many operators, although filtering reduces the amount of chatter to the API server from `Reconcile()`. They are particularly useful for controllers that watch resources cluster-wide, i.e. without a namespace.

[doc_event]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/event
[doc_source]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/source#Source
[doc_eventhandler]: https://godoc.org/sigs.k8s.io/controller-runtime/pkg/handler#EventHandler
[doc_predicate]: https://godoc.org/github.com/kubernetes-sigs/controller-runtime/pkg/predicate
[doc_object_metadata]: https://godoc.org/k8s.io/apimachinery/pkg/apis/meta/v1#Object
