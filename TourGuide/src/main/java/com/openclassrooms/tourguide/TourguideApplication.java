package com.openclassrooms.tourguide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TourguideApplication {

	public static void main(String[] args) {
		SpringApplication.run(TourguideApplication.class, args);
	}

	// https://linuxtut.com/fr/1310ddf3b70382008d10/
	//https://www.baeldung.com/java-executor-service-tutorial
	// https://www.jmdoudoux.fr/java/dej/chap-planification_taches.htm



	/*
		Parallelism and Concurrency :

		1. Concurrency (Concurrence)
		Définition : La capacité à gérer plusieurs tâches en même temps (au niveau logique), mais pas nécessairement simultanément.

		Exemple :
		Imaginons un seul processeur (CPU) :
		Il passe rapidement d'une tâche à l'autre (par partage du temps).
		Il semble faire plusieurs choses en même temps, mais en réalité, il alterne entre elles.

		Caractéristiques :
		Plusieurs tâches progressent en parallèle, mais pas exécutées exactement au même moment.
		Utilisé dans les applications avec multithreading (ex : interface utilisateur qui reste fluide pendant un traitement en arrière-plan).
		Gère l’interaction entre tâches : verrouillage, synchronisation, etc.


		2. Parallelism (Parallélisme)
		Définition : L'exécution réelle et simultanée de plusieurs tâches, généralement sur plusieurs processeurs ou cœurs.

		Exemple :
		Si tu as un processeur avec 4 cœurs :
		Tu peux exécuter 4 tâches en même temps.
		Chaque tâche est affectée à un cœur différent.

		Caractéristiques :
		Requiert un matériel multi-cœur.
		Idéal pour les calculs intensifs (ex : traitement de données, rendu 3D, machine learning...).
		Moins concerné par la coordination entre tâches, plus focalisé sur la performance brute.
	 */



	/*
		Parallelized Streams & Api Streams

		1. Streams API (standard)
		Ce sont des flux de données qui permettent de traiter des collections de manière fonctionnelle (map, filter, reduce...).

		Exemple classique :
		List<String> list = Arrays.asList("a", "b", "c", "d");

		list.stream()
			.map(String::toUpperCase)
			.forEach(System.out::println);

		Caractéristiques :
		Utilise un seul thread.
		Les éléments sont traités séquentiellement, un par un.
		Prédictible et simple à déboguer.


		2. Parallel Streams (ou Parallelized Streams)
		Ce sont des flux qui divisent automatiquement les données pour les traiter en parallèle sur plusieurs cœurs.

		Même exemple, mais en parallèle :

		list.parallelStream()
			.map(String::toUpperCase)
			.forEach(System.out::println);

		Caractéristiques :
		Utilise le ForkJoinPool pour répartir les tâches.
		Traite les éléments en parallèle (simultanément sur plusieurs threads).
		Peut accélérer le traitement de gros volumes de données.
		L’ordre de sortie n’est pas garanti (sauf si .forEachOrdered() est utilisé).

		Attention :
		Ne pas utiliser parallelStream() pour des traitements avec effets de bord
		(comme écrire dans un fichier partagé ou une base sans synchronisation).
		Peut être contre-productif pour de petites listes (le coût de la parallélisation dépasse le gain).

		Quand utiliser parallelStream() ?
		Données très volumineuses.
		Opérations pures et indépendantes (sans effet de bord).
		Environnements multi-cœurs.
	 */


	/*
	 Runnable Threads and Callables :

	 1. Runnable — le plus simple
	 Interface qui définit une tâche sans retour de valeur, ni gestion d'exception déclarée.

	Exemple :
	Runnable task = () -> {
		System.out.println("Tâche exécutée par un thread.");
	};

	Thread thread = new Thread(task);
	thread.start();

	Caractéristiques :
	Méthode : void run()
	Pas de retour de valeur
	Pas de levée d’exception vérifiée (checked exceptions)
	Utilisé avec Thread ou ExecutorService.submit() sans retour


	 2. Callable — plus puissant
	Interface conçue pour les tâches avec un retour de valeur et gestion des exceptions.

	Exemple :
	Callable<String> task = () -> {
		return "Résultat de la tâche";
	};

	ExecutorService executor = Executors.newSingleThreadExecutor();
	Future<String> future = executor.submit(task);

	String result = future.get(); // Bloque jusqu'à ce que la tâche soit terminée
	System.out.println(result);

	executor.shutdown();

	Caractéristiques :
	Méthode : T call() throws Exception
	Retourne une valeur (type générique T)
	Peut lancer une exception vérifiée
	Nécessite souvent un ExecutorService

	 */



	/*
	Thread Pools and Futures

	Thread Pool — Gestionnaire de threads
	C’est un réservoir (pool) de threads réutilisables pour exécuter des tâches de façon efficace.

	But :
	Éviter de créer/détruire un thread à chaque tâche.
	Améliorer les performances (surtout avec beaucoup de petites tâches).
	Contrôler le nombre maximal de threads actifs.

	Exemple :
	ExecutorService executor = Executors.newFixedThreadPool(4);
	executor.submit(() -> {
		System.out.println("Tâche dans un thread du pool");
	});

	executor.shutdown();

	Caractéristiques :
	Créé via Executors.newFixedThreadPool(n) ou newCachedThreadPool()
	Exécute des Runnable ou Callable
	Gère le lifecycle des threads automatiquement


	2. Future — Résultat d'une tâche asynchrone
	C’est un objet représentant le résultat d’une tâche exécutée en arrière-plan, que l’on peut récupérer plus tard.

	But :
	Suivre une tâche lancée dans un thread.
	Récupérer un résultat retourné par un Callable.
	Vérifier si la tâche est terminée (isDone()), ou l’annuler (cancel()).

	Exemple :
	Callable<String> task = () -> "Hello";

	ExecutorService executor = Executors.newSingleThreadExecutor();
	Future<String> future = executor.submit(task);

	String result = future.get(); // Bloque jusqu’à la fin de la tâche
	System.out.println(result);

	executor.shutdown();

	Résumé imagé :
	Le Thread Pool est une usine qui exécute des tâches avec ses ouvriers (threads).
	Le Future est le reçu que tu obtiens pour suivre une commande (tâche) passée à cette usine.

	 */



	/*
	Combat Shared-Mutability Using Atomic Variables
	Le concept "Combat Shared Mutability using Atomic Variables" (Combattre la mutabilité partagée avec les variables atomiques)
	est central en programmation concurrente.

	Problème : Shared Mutability (Mutabilité partagée)
	Mutabilité partagée signifie que plusieurs threads accèdent et modifient la même variable en mémoire.

	Danger :
	Peut entraîner des conditions de course (race conditions).
	Résultats imprévisibles ou erronés.
	Exemple : incrémentation non atomique d’un entier partagé.

	Exemple problématique :
	int counter = 0;

	Runnable task = () -> {
		for (int i = 0; i < 1000; i++) {
			counter++; // NON SÉCURISÉ : plusieurs threads modifient counter
		}
	};
	Même si plusieurs threads exécutent ce code, counter n’atteindra jamais 1000 * nombre de threads à cause des accès concurrents.


	Solution : Atomic Variables
	Les classes atomiques comme AtomicInteger, AtomicBoolean, etc., permettent de manipuler des variables de façon sécurisée entre threads.

	Pourquoi "atomique" ?
	Une opération atomique est indivisible : aucun autre thread ne peut l’interrompre au milieu.

	Exemple avec AtomicInteger :
	import java.util.concurrent.atomic.AtomicInteger;
	AtomicInteger counter = new AtomicInteger(0);

	Runnable task = () -> {
		for (int i = 0; i < 1000; i++) {
			counter.incrementAndGet(); // opération atomique
		}
	};
	Ici, counter.incrementAndGet() est thread-safe :

	Pas besoin de synchronized
	Aucun risque de condition de course
	Performance élevée (meilleur que locks dans certains cas)

	 */




	/*
	Avoid Thread Interference via Synchronization and Guarded Blocks

	1. Synchronisation (synchronized)
	But : empêcher que deux threads accèdent simultanément à un morceau de code critique.

	Exemple :
	public class Counter {
		private int count = 0;

		public synchronized void increment() {
			count++;
		}

		public synchronized int getCount() {
			return count;
		}
	}

	Explication :
	Le mot-clé synchronized garantit qu'un seul thread à la fois peut exécuter la méthode increment().
	Cela évite que deux threads modifient count en même temps (thread interference).
	Cela verrouille l’objet (this) ou un autre verrou explicite.


	2. Blocs protégés (Guarded Blocks)
	But : faire en sorte qu’un thread attende qu’une certaine condition soit vraie avant d’agir.

	Utilisé avec :
	les méthodes wait(), notify(), et notifyAll()

	souvent dans des structures comme des producteurs/consommateurs

	Exemple :
	class Drop {
		private String message;
		private boolean empty = true;

		public synchronized String take() {
			while (empty) {
				try {
					wait();  // Attendre qu'un message soit disponible
				} catch (InterruptedException e) { }
			}
			empty = true;
			notifyAll();  // Réveiller les producteurs
			return message;
		}

		public synchronized void put(String message) {
			while (!empty) {
				try {
					wait();  // Attendre que le message soit consommé
				} catch (InterruptedException e) { }
			}
			empty = false;
			this.message = message;
			notifyAll();  // Réveiller les consommateurs
		}
	}

	Explication :
	Le guarded block est ici la boucle while (!empty).
	Il garantit que le thread ne continue que si une certaine condition est remplie.
	Cela coordonne l'exécution entre plusieurs threads.

	En résumé :
	Synchronisation = verrouillage pour éviter les conflits d’accès
	Guarded block = attente active d’une condition précise, généralement utilisée avec la synchronisation


	 */


	/*
	Coordinating Between Threads Using Reentrant Locks:

	Qu’est-ce qu’un ReentrantLock ?
	ReentrantLock est une classe du package java.util.concurrent.locks qui fournit un verrou explicite.
	Il est réentrant, ce qui signifie que le même thread peut acquérir plusieurs fois le verrou sans se bloquer.

	Exemple simple :
	Lock lock = new ReentrantLock();

	lock.lock(); // thread acquiert le verrou
	try {
		// section critique
	} finally {
		lock.unlock(); // important de toujours libérer le verrou
	}
	Coordination entre threads avec ReentrantLock
	Pour coordonner plusieurs threads (par exemple, un producteur et un consommateur),
	on peut utiliser ReentrantLock avec des conditions (Condition) :

	Lock lock = new ReentrantLock();
	Condition notEmpty = lock.newCondition();
	Condition notFull = lock.newCondition();
	Cela permet d’avoir un contrôle finer-grained sur la synchronisation que synchronized.

	Extrait d’exemple producteur/consommateur :
	public class BlockingQueueExample {
		private Queue<Integer> queue = new LinkedList<>();
		private int capacity = 10;

		private Lock lock = new ReentrantLock();
		private Condition notFull = lock.newCondition();
		private Condition notEmpty = lock.newCondition();

		public void put(int value) throws InterruptedException {
			lock.lock();
			try {
				while (queue.size() == capacity) {
					notFull.await(); // attend que le queue ne soit plus pleine
				}
				queue.add(value);
				notEmpty.signal(); // réveille un consommateur
			} finally {
				lock.unlock();
			}
		}

		public int take() throws InterruptedException {
			lock.lock();
			try {
				while (queue.isEmpty()) {
					notEmpty.await(); // attend qu’il y ait un élément
				}
				int value = queue.remove();
				notFull.signal(); // réveille un producteur
				return value;
			} finally {
				lock.unlock();
			}
		}
	}

	Quand utiliser ReentrantLock ?
	Utilisez ReentrantLock plutôt que synchronized si :
	Vous avez besoin de plusieurs conditions d’attente (comme dans un buffer avec producteur/consommateur).
	Vous avez besoin de verrouillage avec timeout ou de pouvoir interrompre un thread en attente du verrou.
	Vous avez besoin d’un contrôle plus précis sur le moment où le verrou est acquis ou libéré.

	 */


	/*
	Restrict Access to Limited Resources With Semaphores:

	"Restrict Access to Limited Resources With Semaphores" signifie "Restreindre l'accès à des ressources limitées
	 à l'aide de sémaphores". C'est un concept de programmation concurrente, utilisé notamment pour contrôler
	 l'accès à des ressources partagées par plusieurs threads ou processus.

	 1. Contexte du problème
	Imagine que tu as une ressource limitée, comme :
	Un nombre fixe de connexions à une base de données
	Un nombre limité de places de parking

	Quelques imprimantes disponibles
	Et plusieurs threads/processus essaient d'y accéder en même temps.
	Si tu ne limites pas l'accès, tu risques :
	Des conflits
	Des erreurs (ex : plusieurs threads utilisent la même ressource en même temps)
	Une surcharge (ex : 10 utilisateurs pour 5 connexions)

	2. Qu’est-ce qu’un sémaphore ?
	Un sémaphore est un outil de synchronisation qui agit comme un compteur pour réguler le nombre
	 de threads pouvant accéder simultanément à une ressource.

	Deux types de sémaphores :
	Sémaphore binaire (valeur 0 ou 1) : comme un verrou (lock), permet l'accès à un seul thread.
	Sémaphore comptable (compteur > 1) : permet à un nombre limité de threads d’accéder à la ressource.

	3. Fonctionnement du sémaphore comptable (ex : 3 connexions max)
	Le sémaphore est initialisé à 3 (3 ressources disponibles)
	Chaque thread qui veut accéder à la ressource fait acquire() :
	Si le compteur > 0 : il est décrémenté, le thread entre
	Si le compteur == 0 : le thread attend
	Une fois la ressource libérée, le thread fait release(), le compteur est incrémenté

	4. Exemple en Java
	import java.util.concurrent.Semaphore;

	public class ConnectionPool {
		private static final Semaphore semaphore = new Semaphore(3); // 3 connexions max

		public void useResource(String threadName) {
			try {
				System.out.println(threadName + " tente d'accéder à la ressource...");
				semaphore.acquire(); // prend une place
				System.out.println(threadName + " a obtenu l'accès !");
				Thread.sleep(2000); // simule l'utilisation
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				System.out.println(threadName + " libère la ressource.");
				semaphore.release(); // libère une place
			}
		}
	}

	public class Main {
		public static void main(String[] args) {
			ConnectionPool pool = new ConnectionPool();

			for (int i = 1; i <= 6; i++) {
				String name = "Thread-" + i;
				new Thread(() -> pool.useResource(name)).start();
			}
		}
	}
	Ici, seulement 3 threads peuvent utiliser la ressource en même temps. Les autres attendent leur tour.

	5. Utilité des sémaphores
	Contrôler l’accès à des ressources limitées (fichiers, connexions, sockets…)
	Éviter les conflits ou blocages
	Gérer le flux concurrent dans une application multi-threadée
	 */



	/*
	Ensure Work Is Completed in the Right Order With Countdown Latches:
	En Java, le concept "Ensure work is completed in the right order with Countdown Latches" signifie utiliser
	des comptes à rebours synchronisés pour forcer l'ordre d'exécution entre plusieurs threads.
	Ce concept est implémenté via la classe CountDownLatch du package java.util.concurrent.

	Définition de CountDownLatch
	CountDownLatch est un outil de synchronisation qui permet à un ou plusieurs threads d'attendre jusqu'à ce qu’un
	ensemble d’opérations effectuées par d’autres threads soit terminé.

	Fonctionnement
	Un CountDownLatch est initialisé avec un compteur (généralement le nombre de threads ou de tâches à attendre).
	Chaque fois qu’un thread termine son travail, il appelle la méthode countDown(), ce qui décrémente le compteur.
	Le ou les threads qui attendent appellent la méthode await() — ils seront bloqués tant que le compteur n'atteint pas zéro.

	Objectif : Assurer l’ordre d’exécution
	On utilise CountDownLatch pour :
	Démarrer une tâche uniquement après que d’autres aient terminé.
	Par exemple : Démarrer le traitement principal uniquement après que des initialisations parallèles soient terminées.

	Exemple pratique
	Objectif : Démarrer le traitement principal après que 3 tâches d'initialisation soient terminées
	import java.util.concurrent.CountDownLatch;

	public class Main {
		public static void main(String[] args) throws InterruptedException {
			CountDownLatch latch = new CountDownLatch(3); // 3 tâches à attendre

			// Tâches d'initialisation dans des threads séparés
			for (int i = 1; i <= 3; i++) {
				int taskId = i;
				new Thread(() -> {
					System.out.println("Initialisation de la tâche " + taskId);
					try {
						Thread.sleep(1000 * taskId); // Simulation d’un travail
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.out.println("Tâche " + taskId + " terminée.");
					latch.countDown(); // Signale que cette tâche est terminée
				}).start();
			}

			// Attendre que toutes les initialisations soient terminées
			latch.await();

			// Ce code ne s'exécutera qu'après la fin des 3 threads ci-dessus
			System.out.println("Toutes les initialisations sont terminées. Traitement principal démarré.");
		}
	}
	Résultat attendu

	Initialisation de la tâche 1
	Initialisation de la tâche 2
	Initialisation de la tâche 3
	Tâche 1 terminée.
	Tâche 2 terminée.
	Tâche 3 terminée.
	Toutes les initialisations sont terminées. Traitement principal démarré.

	Utilisations typiques
	Initialiser des services dans le bon ordre.
	Synchroniser la fin de tâches parallèles.
	Écrire des tests qui attendent la fin d’un traitement asynchrone.
	 */



	/*
	Connect Concurrent Actions Using CompletableFutures

	Le concept "Connect Concurrent Actions Using CompletableFuture" en Java permet de chaîner et de combiner plusieurs
	tâches concurrentes (asynchrones) de manière fluide, lisible et non bloquante, en utilisant la classe CompletableFuture
	introduite avec Java 8.

	Qu'est-ce que CompletableFuture ?
	CompletableFuture est une classe du package java.util.concurrent qui permet :
	d’exécuter une tâche asynchrone (dans un thread séparé),
	d’enchaîner d’autres actions après son achèvement,
	de combiner plusieurs futures,
	de gérer les erreurs.

	Pourquoi "Connect Concurrent Actions" ?
	Dans un programme multi-threads, il est courant d'avoir plusieurs tâches parallèles. Le but de CompletableFuture est de :

	Exécuter ces tâches en parallèle, puis
	Les connecter entre elles, par exemple : exécuter une tâche B après la tâche A, ou exécuter C quand A et B sont terminées.

	Avantages
	Code plus lisible et fluide que les Future classiques ou les ExecutorService.
	Permet d’éviter les blocages explicites (.get()).

	Facilite la composition des tâches asynchrones.

	Exemple de base : chaîner deux actions
	import java.util.concurrent.CompletableFuture;

	public class Main {
		public static void main(String[] args) {
			CompletableFuture<Void> future = CompletableFuture
				.supplyAsync(() -> {
					// Tâche 1 : traitement long
					System.out.println("Chargement des données...");
					sleep(1000);
					return "Données brutes";
				})
				.thenApply(data -> {
					// Tâche 2 : transformation
					System.out.println("Traitement des données...");
					return data.toUpperCase();
				})
				.thenAccept(result -> {
					// Tâche 3 : affichage
					System.out.println("Résultat final : " + result);
				});

			// Attendre la fin (sinon le programme se termine avant)
			future.join();
		}

		static void sleep(int ms) {
			try { Thread.sleep(ms); } catch (InterruptedException e) { e.printStackTrace(); }
		}
	}
	Exemple : combiner plusieurs tâches parallèles
	import java.util.concurrent.CompletableFuture;

	public class CombineTasks {
		public static void main(String[] args) {
			CompletableFuture<String> task1 = CompletableFuture.supplyAsync(() -> {
				sleep(1000);
				return "Résultat A";
			});

			CompletableFuture<String> task2 = CompletableFuture.supplyAsync(() -> {
				sleep(1500);
				return "Résultat B";
			});

			// Combiner les deux résultats quand les deux sont terminés
			CompletableFuture<String> combined = task1.thenCombine(task2, (a, b) -> a + " + " + b);

			System.out.println("Attente des résultats combinés...");
			System.out.println("Résultat combiné : " + combined.join()); // join() = attend et retourne le résultat
		}

		static void sleep(int ms) {
			try { Thread.sleep(ms); } catch (InterruptedException e) { e.printStackTrace(); }
		}
	}

	Cas d'utilisation typiques
	Appels API parallèles (ex: microservices).
	Traitements en pipeline (ex: validation → transformation → sauvegarde).
	Interfaces utilisateurs réactives.
	Chargement parallèle de fichiers ou de données.
	 */



	/*
	Create a Recursive Solution Using Fork/Join:

	Créer une solution récursive utilisant le framework Fork/Join en Java permet d'exécuter des tâches de manière
	parallèle en tirant parti des processeurs multicœurs. C’est idéal pour des algorithmes "diviser pour mieux régner"
	(divide and conquer), comme les tri-fusions, sommation parallèle, parcours d’arbre, etc.

	Concepts de base à comprendre
	Fork : divise la tâche en sous-tâches.
	Join : attend que les sous-tâches soient terminées et combine leurs résultats.
	RecursiveTask<V> : pour des tâches qui retournent une valeur.
	RecursiveAction : pour des tâches qui ne retournent rien.

	Exemple : Somme d’un tableau avec Fork/Join
	Objectif : Diviser un tableau en parties, additionner les parties en parallèle, puis combiner les résultats.

	import java.util.concurrent.RecursiveTask;
	import java.util.concurrent.ForkJoinPool;

	public class ParallelSum extends RecursiveTask<Long> {
		private static final int THRESHOLD = 1000;
		private long[] numbers;
		private int start;
		private int end;

		public ParallelSum(long[] numbers, int start, int end) {
			this.numbers = numbers;
			this.start = start;
			this.end = end;
		}

		@Override
		protected Long compute() {
			int length = end - start;

			//Si la taille de la tâche est petite, on la fait directement
			if (length <= THRESHOLD) {
				long sum = 0;
				for (int i = start; i < end; i++) {
					sum += numbers[i];
				}
				return sum;
			}

			//Sinon, on divise en deux tâches
			int mid = start + length / 2;

			ParallelSum leftTask = new ParallelSum(numbers, start, mid);
			ParallelSum rightTask = new ParallelSum(numbers, mid, end);

			//Fork (exécute en parallèle)
			leftTask.fork();

			//On calcule la droite pendant que la gauche est en attente
			long rightResult = rightTask.compute();

			//Join (attend le résultat de la gauche)
			long leftResult = leftTask.join();

			return leftResult + rightResult;
		}

		Méthode principale
		public static void main(String[] args) {
			long[] numbers = new long[10_000];
			for (int i = 0; i < numbers.length; i++) {
				numbers[i] = i + 1;
			}

			ForkJoinPool pool = new ForkJoinPool(); // utilise tous les cœurs disponibles

			ParallelSum task = new ParallelSum(numbers, 0, numbers.length);
			long result = pool.invoke(task);

			System.out.println("Total sum = " + result);
		}
	}

	Avantages
	Exploite efficacement les CPU multi-cœurs
	Très adapté aux tâches récursives de type divide-and-conquer
	Abstraction plus facile que les threads manuels

	 */


	/*
	Implement a Producer-Consumer Pattern Using a BlockingQueue

	Le Producer-Consumer Pattern (producteur-consommateur) est un modèle de conception classique utilisé pour synchroniser
	l'accès à une ressource partagée entre plusieurs threads. En Java, la classe BlockingQueue facilite grandement son implémentation.

	Objectif du Pattern :
	Producer (Producteur) : génère des données et les place dans une file d’attente.
	Consumer (Consommateur) : récupère les données de la file d’attente pour les traiter.
	BlockingQueue : permet de stocker les données en assurant la synchronisation entre producteurs et consommateurs automatiquement.

	Pourquoi BlockingQueue ?
	Elle gère la synchronisation automatiquement (pas besoin de wait()/notify()).
	Si la file est pleine : put() bloque le producteur.
	Si la file est vide : take() bloque le consommateur.

	Implémentation simple en Java :
	1. Importer les bonnes classes :
	import java.util.concurrent.BlockingQueue;
	import java.util.concurrent.ArrayBlockingQueue;
	2. Classe Producteur :
	public class Producer implements Runnable {
		private BlockingQueue<Integer> queue;

		public Producer(BlockingQueue<Integer> queue) {
			this.queue = queue;
		}

		@Override
		public void run() {
			try {
				int i = 0;
				while (true) {
					System.out.println("Producing: " + i);
					queue.put(i); // bloque si la queue est pleine
					i++;
					Thread.sleep(500); // simule du travail
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
	3. Classe Consommateur :
	public class Consumer implements Runnable {
		private BlockingQueue<Integer> queue;

		public Consumer(BlockingQueue<Integer> queue) {
			this.queue = queue;
		}

		@Override
		public void run() {
			try {
				while (true) {
					Integer value = queue.take(); // bloque si la queue est vide
					System.out.println("Consumed: " + value);
					Thread.sleep(1000); // simule du traitement
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
	4. Main Class pour lancer les threads :
	public class Main {
		public static void main(String[] args) {
			BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5); // capacité de 5

			Thread producerThread = new Thread(new Producer(queue));
			Thread consumerThread = new Thread(new Consumer(queue));

			producerThread.start();
			consumerThread.start();
		}
	}
	Résultat attendu :
	Le producteur insère des nombres (0, 1, 2, …) dans la queue.
	Le consommateur les retire et les affiche.
	Si le consommateur est lent → le producteur attend (car la queue est pleine).
	Si le producteur est lent → le consommateur attend (car la queue est vide).

	Avantages de BlockingQueue :
	Code plus simple (pas de synchronized, wait, notify).
	Sécurité et performance optimales pour le multithread.
	Parfait pour les architectures producteur/consommateur, traitement parallèle, pipelines de données, etc.
	*/



	/*
	Simplify Map Sharing Using ConcurrentHashMap:

	En Java multi-thread, lorsque plusieurs threads doivent accéder et modifier une structure de données partagée
	comme une Map, il faut faire attention à la concurrence (accès simultanés). La ConcurrentHashMap
	est une implémentation thread-safe de Map qui facilite ce partage sans avoir à gérer la synchronisation manuellement.

	Problème à résoudre
	Une HashMap n'est pas thread-safe. Si plusieurs threads lisent/écrivent dedans en même temps → résultats incohérents,
	erreurs (ConcurrentModificationException).

	On pourrait synchroniser avec Collections.synchronizedMap(...), mais c’est moins performant (verrou global).

	Solution : ConcurrentHashMap
	C’est une version de Map optimisée pour les accès concurrents :
	Pas de verrou global : elle divise la map en segments et verrouille seulement les parties modifiées.
	Meilleures performances que synchronizedMap.
	Lecture très rapide même quand d’autres threads écrivent.

	Exemple simple : accès concurrent à une ConcurrentHashMap
	import java.util.concurrent.ConcurrentHashMap;

	public class SharedMapExample {
		public static void main(String[] args) {
			ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

			// Thread 1 - ajoute des entrées
			Thread writer = new Thread(() -> {
				for (int i = 1; i <= 5; i++) {
					map.put("key" + i, i);
					System.out.println("Writer: key" + i + " => " + i);
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});

			// Thread 2 - lit les entrées
			Thread reader = new Thread(() -> {
				for (int i = 1; i <= 5; i++) {
					Integer value = map.get("key" + i);
					System.out.println("Reader: key" + i + " => " + value);
					try {
						Thread.sleep(700);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});

			writer.start();
			reader.start();
		}
	}
	Comportement :
	writer ajoute des paires key1=1, key2=2, etc.
	reader tente de lire les valeurs en parallèle.
	Même si les accès sont simultanés, aucune erreur ni données corrompues, grâce à ConcurrentHashMap.

	Quand l'utiliser ?
	Partage de données entre plusieurs threads sans avoir à synchroniser à la main.
	Applications web, caches, gestion d’état partagé entre threads, etc.

	Remarques importantes :
	Certaines opérations ne sont pas atomiques à moins d'utiliser des méthodes spéciales :
	map.putIfAbsent(key, value)
	map.compute(key, ...)
	map.merge(key, ...)
	Ne jamais faire : if (!map.containsKey(k)) map.put(k, v); car entre les deux instructions, un autre thread peut intervenir.
	*/



	/*
	Modify Arrays on Multiple Threads With CopyOnWriteArrayList

	En Java, CopyOnWriteArrayList est une implémentation de la collection List qui est thread-safe, c’est-à-dire qu’elle
	peut être utilisée par plusieurs threads en même temps sans provoquer d’erreurs de concurrence.
	Voici une explication pédagogique du fonctionnement de CopyOnWriteArrayList et de son utilisation dans un
	 contexte multi-thread (thread en Java) :

	Qu’est-ce que CopyOnWriteArrayList ?
	CopyOnWriteArrayList fait partie du package java.util.concurrent. Contrairement à ArrayList, elle est conçue pour
	fonctionner correctement dans un environnement multi-threadé, sans qu’on ait besoin de synchroniser manuellement les accès.

	Principe :
	À chaque fois qu’un thread modifie la liste (ajout, suppression, etc.), une nouvelle copie de la liste entière est créée.
	Les itérateurs (for, foreach) fonctionnent sur une copie "figée" de la liste, ce qui les rend sûrs contre les ConcurrentModificationException.

	Exemple simple avec plusieurs threads
	import java.util.List;
	import java.util.concurrent.CopyOnWriteArrayList;

	public class CopyOnWriteExample {
		public static void main(String[] args) {
			List<String> list = new CopyOnWriteArrayList<>();

			// Thread 1 : ajoute des éléments
			Thread writerThread = new Thread(() -> {
				for (int i = 1; i <= 5; i++) {
					list.add("Élément " + i);
					System.out.println("Ajouté : Élément " + i);
					try {
						Thread.sleep(100); // Pause pour simuler un traitement
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});

			// Thread 2 : lit la liste
			Thread readerThread = new Thread(() -> {
				for (int i = 0; i < 5; i++) {
					System.out.println("Lecture : " + list);
					try {
						Thread.sleep(150); // Pause plus longue
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});

			writerThread.start();
			readerThread.start();
		}
	}
	Pourquoi l'utiliser ?
	Sécurité en lecture/écriture sans synchronized
	Aucun risque de ConcurrentModificationException quand on itère sur la liste
	Très bon choix si on lit souvent et qu’on écrit rarement

	4. Inconvénients
	Moins performant pour beaucoup de modifications (car une copie de la liste est faite à chaque modification)
	Pas adapté si les écritures sont fréquentes et que la liste est grande

	5. Quand utiliser CopyOnWriteArrayList ?
	Utilisez-la si :
	Vous avez beaucoup de lectures et peu d’écritures
	Plusieurs threads peuvent accéder à la liste en même temps
	Vous voulez éviter la complexité de synchronisation manuelle
	*/


	/*
	Identify the Applications of Reactive Streams in Java

	En Java, Reactive Streams est une spécification introduite pour gérer le flux asynchrone de données non bloquantes
	avec gestion de la pression (backpressure). Elle est particulièrement utile dans les systèmes où la performance,
	la scalabilité et la réactivité sont cruciales.

	Voici une explication claire et pédagogique des applications (cas d’usage) des Reactive Streams en Java :

	Applications des Reactive Streams en Java
	1. Traitement de données asynchrones
	Utilité : Lire et traiter des données au fil de l’eau (streaming) sans bloquer les threads.

	Exemple : Lire un gros fichier ligne par ligne ou streamer les logs d’une application en temps réel.
	Librairies : Project Reactor, RxJava, Akka Streams.

	2. Services Web Réactifs (WebFlux)
	Utilité : Créer des APIs REST non bloquantes, capables de gérer un grand nombre de requêtes simultanées.

	Exemple : Spring WebFlux, qui repose sur Reactor et Reactive Streams.
	Avantage : Réduit le nombre de threads utilisés, améliore la scalabilité.

	3. Communication entre microservices
	Utilité : Échanger des flux de données en temps réel entre services (via WebSocket, Kafka, etc.).

	Exemple : Un microservice envoie un flux d’événements à un autre service via Apache Kafka en mode réactif.
	Grâce au backpressure, le producteur s’adapte à la vitesse du consommateur.

	4. Accès aux bases de données réactives
	Utilité : Interroger une base de données sans bloquer les threads (I/O non bloquant).

	Exemple : Utilisation de R2DBC pour PostgreSQL ou MongoDB Reactive Streams Driver.
	Idéal pour les applications qui font beaucoup d'accès à la base avec peu de logique métier.

	5. Streaming de données en temps réel
	Utilité : Gérer le traitement de données en continu (comme des capteurs IoT ou du trading).
	Exemple : Traiter un flux de données financières pour détecter des anomalies ou faire de l’analyse en direct.

	6. Systèmes à haute disponibilité et scalables
	Utilité : Réduire la consommation mémoire et CPU dans des systèmes à fort trafic.
	Exemple : Une application réactive peut gérer plus d’utilisateurs avec moins de ressources que son équivalent
	classique (Spring MVC, par exemple).
	 */



	/*
	Use Reactive Streams and RxJava for Asynchronous Programming

	1. Qu’est-ce que la Programmation Réactive ?
	La programmation réactive est un paradigme basé sur des flux de données asynchrones.
	Elle permet à ton application de réagir aux événements dès qu’ils arrivent, sans bloquer les threads.

	Exemple simple :
	Au lieu de dire « va chercher les données et attends qu’elles reviennent »,
	tu dis « va chercher les données et préviens-moi quand c’est prêt ».

	2. Reactive Streams : C’est quoi ?
	Reactive Streams est une spécification (Java 9+) définissant un standard pour gérer les flux de données asynchrones
	avec gestion de la pression (backpressure).

	Les 4 interfaces principales :
	Interface	Rôle
	Publisher<T>	Fournit les données (émissions)
	Subscriber<T>	Reçoit les données (écoute)
	Subscription	Gère l’abonnement (start, cancel, demander n éléments)
	Processor<T,R>	Combine Publisher et Subscriber pour transformer les données

	Exemple logique :
	Publisher<Integer> publisher = ...
	Subscriber<Integer> subscriber = ...
	publisher.subscribe(subscriber);
	3. RxJava : Une implémentation puissante
	RxJava (Reactive Extensions for Java) est une bibliothèque qui implémente les Reactive Streams avec beaucoup d'opérateurs prêts à l’emploi.

	Les types principaux en RxJava :
	Type	Description
	Observable<T>	Émet 0..n éléments (sans backpressure)
	Flowable<T>	Émet 0..n éléments (avec gestion de backpressure)
	Single<T>	Émet exactement 1 élément ou une erreur
	Maybe<T>	Émet 0 ou 1 élément
	Completable	Émet juste la complétion ou une erreur

	4. Exemple RxJava simple
	Objectif : Récupérer une liste de noms et les afficher en majuscules

	Observable.just("Alice", "Bob", "Charlie")
		.map(String::toUpperCase)
		.subscribe(
			name -> System.out.println("Nom : " + name),
			error -> System.err.println("Erreur : " + error),
			() -> System.out.println("Terminé")
		);
	Output :
	yaml
	Modifier
	Nom : ALICE
	Nom : BOB
	Nom : CHARLIE
	Terminé
	Exemple avec Flowable et gestion de backpressure

	Flowable.range(1, 1000)
		.onBackpressureBuffer()
		.observeOn(Schedulers.io())
		.subscribe(
			item -> {
				Thread.sleep(10); // simulate slow consumer
				System.out.println("Reçu : " + item);
			}
		);
	Ici, onBackpressureBuffer() stocke les éléments pour ne pas en perdre quand le consommateur est lent.

	6. Pourquoi utiliser RxJava ou Reactive Streams ?
	Asynchrone et non bloquant
	Très utile pour les services web, apps mobiles, interfaces réactives
	Gestion native de la concurrence
	Réduction du code impératif complexe
	 */



	/*
	Est-ce qu’on a encore besoin de threads avec Reactive Streams et RxJava ?

	Oui, il y a toujours des threads, MAIS tu ne les gères pas toi-même.

	Ce qui change :
	Avec RxJava et Reactive Streams, tu délègues la gestion des threads à la bibliothèque (RxJava, Reactor...), via des Schedulers.
	Ce qu'on faisait avant : threads manuels

	new Thread(() -> {
		// Code asynchrone
	}).start();
	Inconvénient : Tu dois créer, démarrer, gérer et synchroniser les threads toi-même.

	Ce qu'on fait avec RxJava : threads automatiques
	RxJava propose des Schedulers pour définir où et comment le code s'exécute.

	Scheduler	Utilisation typique
	Schedulers.io()	Opérations IO (appel REST, accès BDD, etc.)
	Schedulers.computation()	Calculs intensifs (CPU-bound)
	Schedulers.newThread()	Nouveau thread à chaque fois
	Schedulers.single()	Thread unique partagé
	Schedulers.trampoline()	Exécution en séquence sur le thread courant

	Exemple : gestion implicite des threads avec RxJava

	Observable.just("Alice", "Bob", "Charlie")
		.subscribeOn(Schedulers.io())          // Produit les données dans un thread IO
		.observeOn(Schedulers.computation())   // Consomme les données dans un thread CPU
		.map(String::toUpperCase)
		.subscribe(name -> {
			System.out.println(Thread.currentThread().getName() + " -> " + name);
		});
	Tu ne crées aucun thread toi-même. RxJava s’en occupe via les Schedulers.

	Conclusion
	Reactive Streams + RxJava n’éliminent pas les threads
	Mais tu ne les gères plus manuellement
	Cela te permet de :
	Éviter les bugs liés à la concurrence
	Être asynchrone et performant
	Profiter de la programmation déclarative
	 */
}
